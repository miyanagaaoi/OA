<#
================================================================================
 start-local.ps1 —— 本机（Windows / 无 Docker）一键启动 OA 本地环境
--------------------------------------------------------------------------------
 启动顺序：MySQL 8.0.40 便携版 → Redis 5.0.14.1 便携版 → oa-server（dev profile）

 用法：
   pwsh -File H:\dsh\OA\oa-deploy\runtime\start-local.ps1
   pwsh -File ...\start-local.ps1 -SkipApp        # 只起 MySQL + Redis
   pwsh -File ...\start-local.ps1 -RestartApp     # 只重启应用（MySQL/Redis 已在跑）

 说明：
   - 全部用「便携版 + 独立数据目录」，不注册 Windows 服务、不需要管理员权限。
   - 数据目录、日志、PID 一律落在 oa-deploy\runtime\（.gitignore 内），不污染仓库。
   - 用 .NET Process 而不是 Start-Process 拉起进程：本机环境同时存在
     NO_PROXY/no_proxy、HTTP_PROXY/http_proxy 等仅大小写不同的变量，
     PowerShell 的 Start-Process 会因此抛
     “已添加项。字典中的关键字:NO_PROXY 所添加的关键字:no_proxy” 而无法启动。
================================================================================
#>
[CmdletBinding()]
param(
    [switch]$SkipApp,
    [switch]$RestartApp
)

$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------------ 路径与常量
$Runtime = $PSScriptRoot                                   # H:\dsh\OA\oa-deploy\runtime
$Root    = Split-Path (Split-Path $Runtime -Parent) -Parent # H:\dsh\OA

$Cache      = Join-Path $Root '.cache'
$MySqlHome  = Join-Path $Cache 'mysql\extract\mysql-8.0.40-winx64'
$MySqlExe   = Join-Path $MySqlHome 'bin\mysqld.exe'
$MySqlAdmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
$MySqlCli   = Join-Path $MySqlHome 'bin\mysql.exe'
$MyIni      = Join-Path $Runtime 'mysql\my.ini'

$RedisHome  = Join-Path $Cache 'redis\redis-5.0.14.1'
$RedisExe   = Join-Path $RedisHome 'redis-server.exe'
$RedisCli   = Join-Path $RedisHome 'redis-cli.exe'

$MavenCmd   = 'C:\Tools\apache-maven-3.9.16\bin\mvn.cmd'
$JavaHome   = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'

$MySqlPort = 3306
$RedisPort = 6379
$AppPort   = 8080

$MySqlLog = Join-Path $Runtime 'mysql\error.log'
$RedisLog = Join-Path $Runtime 'redis\redis.log'
$AppLog   = Join-Path $Cache   'oa-server.log'
$AppCmd   = Join-Path $Runtime 'run-app.cmd'

# 数据库账号（application-dev.yml 读的是 OA_DB_USERNAME / OA_DB_PASSWORD）
$DbUser = 'oa'
$DbPass = 'oa_dev_pwd'

# ------------------------------------------------------------------ 小工具
function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok  ($msg) { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!]  $msg" -ForegroundColor Yellow }

function Test-Port([int]$Port, [int]$TimeoutMs = 800) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        if ($task.Wait($TimeoutMs) -and $client.Connected) { return $true }
        return $false
    } catch { return $false } finally { $client.Dispose() }
}

function Wait-Port([int]$Port, [int]$Seconds = 60, [string]$What = 'service') {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $Port) { return $true }
        Start-Sleep -Milliseconds 700
    }
    throw "$What 在 $Seconds 秒内未监听 127.0.0.1:$Port"
}

# 用 .NET 直接拉起「分离」进程；返回 Process 对象
# 注意 1：本机 PowerShell 是 5.1（.NET Framework），没有 ProcessStartInfo.ArgumentList，
#         只能拼 Arguments 字符串；引号需自行处理。
# 注意 2：本机环境同时存在 NO_PROXY/no_proxy 等仅大小写不同的变量，PowerShell 的
#         Start-Process 会因此抛"已添加项。字典中的关键字..."，所以这里用 .NET 直接起。
# 注意 3：经 cmd.exe 起、并把 cmd 自身的 stdout/stderr 重定向到 NUL。
#         否则子进程会继承调用方（终端管道 / CI 收集器）的句柄，脚本退出后调用方
#         仍会一直等待管道关闭（表现为"命令挂住不返回"）。
function Start-Detached([string]$File, [string]$Arguments, [string]$WorkDir) {
    # 【2026-10-02 修复】改用 WMI 创建进程（Win32_Process.Create）。
    #
    # 为什么不能用 .NET Process.Start / cmd start：这两种方式创建的子进程**仍继承调用方的
    # 控制台与管道句柄**。当调用方是「按管道读输出」的宿主（IDE 任务、CI 收集器、Agent 的
    # shell 包装器）时，脚本自己退出了、管道却因孙进程仍持有而**永不关闭**，
    # 调用方就表现为「命令挂住不返回」——实测：子代理执行本脚本后 shell 悬挂 10 分钟以上，
    # 而应用其实早已就绪（health=UP）。
    # WMI 创建的进程挂到 WmiPrvSE 下，**完全脱离调用方句柄表**，脚本可立即返回。
    $cmdLine = if ($Arguments) { "cmd.exe /c `"$File`" $Arguments" } else { "cmd.exe /c `"$File`"" }
    $full = if ($WorkDir) { "cmd.exe /c `"cd /d `"$WorkDir`" && `"$File`" $Arguments`"" } else { $cmdLine }
    $result = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $full }
    if ($result.ReturnValue -ne 0) {
        throw "WMI 创建进程失败：ReturnValue=$($result.ReturnValue)  CommandLine=$full"
    }
    return [pscustomobject]@{ Id = [int]$result.ProcessId; Via = 'WMI' }
}

function Save-Pid([string]$Name, $Ids) {
    ($Ids -join ',') | Set-Content -Path (Join-Path $Runtime "$Name.pid") -Encoding ascii
}

function Get-LogTail([string]$Path, [int]$Lines = 5) {
    if (Test-Path $Path) { Get-Content $Path -Tail $Lines -Encoding UTF8 }
}

# ------------------------------------------------------------------ 前置检查
Write-Step "检查便携版运行时"
foreach ($f in @($MySqlExe, $MyIni, $RedisExe)) {
    if (-not (Test-Path $f)) { throw "缺少文件：$f（请先下载并解压便携版 MySQL / Redis）" }
}
Write-Ok "MySQL  : $MySqlHome"
Write-Ok "Redis  : $RedisHome"
Write-Ok "my.ini : $MyIni"

# ------------------------------------------------------------------ 1) MySQL
Write-Step "MySQL ($MySqlPort)"
if (Test-Port $MySqlPort) {
    Write-Ok "已在运行，跳过启动"
} else {
    $proc = Start-Detached $MySqlExe "--defaults-file=`"$MyIni`"" $MySqlHome
    Wait-Port $MySqlPort 90 'mysqld' | Out-Null
    Write-Ok "已启动"
}
# 统一记录「真实 mysqld 进程」的 PID（Start-Detached 经 cmd.exe 包裹，wrapper 的 PID 不是服务 PID）
$live = @(Get-Process mysqld -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
Save-Pid 'mysqld' $live
Write-Ok "PID=$($live -join ',')  日志：$MySqlLog"
# 就绪校验（root 空口令，便携版 initialize-insecure 的初始状态）
$ver = & $MySqlCli -u root -N -B -e "SELECT CONCAT(VERSION(),' / ',@@character_set_server,' / ',@@collation_server,' / ',@@time_zone,' / trust_fn=',@@log_bin_trust_function_creators);" 2>$null
Write-Ok "参数：$ver"

# ------------------------------------------------------------------ 2) Redis
Write-Step "Redis ($RedisPort)"
if (Test-Port $RedisPort) {
    Write-Ok "已在运行，跳过启动"
} else {
    New-Item -ItemType Directory -Force -Path (Join-Path $Runtime 'redis') | Out-Null
    $proc = Start-Detached $RedisExe (
        "--port $RedisPort --bind 127.0.0.1 --dir `"$(Join-Path $Runtime 'redis')`" " +
        "--appendonly yes --logfile `"$RedisLog`""
    ) $RedisHome
    Wait-Port $RedisPort 30 'redis-server' | Out-Null
    Write-Ok "已启动"
}
$liveRedis = @(Get-Process redis-server -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
Save-Pid 'redis' $liveRedis
Write-Ok "PID=$($liveRedis -join ',')  日志：$RedisLog"
$pong = & $RedisCli -h 127.0.0.1 -p $RedisPort ping
Write-Ok "PING -> $pong"

if ($SkipApp -and -not $RestartApp) {
    Write-Step "已按 -SkipApp 跳过应用启动"
    exit 0
}

# ------------------------------------------------------------------ 3) oa-server
Write-Step "oa-server ($AppPort, profile=dev, Flyway 自动迁移 V1~V4)"
if (Test-Port $AppPort) {
    if (-not $RestartApp) {
        Write-Warn "8080 已被占用，跳过启动（如需重启请加 -RestartApp）"
        exit 0
    }
    Write-Warn "8080 被占用，先停止旧实例"
    & (Join-Path $Runtime 'stop-local.ps1') -OnlyApp
    Start-Sleep -Seconds 3
}

# 生成启动包装脚本：显式设置 JAVA_HOME / Maven / 代理 / 数据源口令，并重定向日志
if (Test-Path $AppLog) {
    Move-Item $AppLog "$AppLog.prev" -Force
}
$cmdBody = @"
@echo off
setlocal
set "JAVA_HOME=$JavaHome"
set "PATH=C:\Tools\apache-maven-3.9.16\bin;%JAVA_HOME%\bin;%PATH%"
set "HTTP_PROXY=http://127.0.0.1:7899"
set "HTTPS_PROXY=http://127.0.0.1:7899"
set "OA_DB_USERNAME=$DbUser"
set "OA_DB_PASSWORD=$DbPass"
cd /d "$Root\oa-server"
call "$MavenCmd" -B -DskipTests spring-boot:run "-Dspring-boot.run.profiles=dev" > "$AppLog" 2>&1
"@
Set-Content -Path $AppCmd -Value $cmdBody -Encoding ascii

$appProc = Start-Detached $AppCmd '' $Runtime
Save-Pid 'app' @($appProc.Id)
Write-Ok "已拉起，cmd PID=$($appProc.Id)  日志：$AppLog"

Write-Host "    等待 /actuator/health ..." -NoNewline
$healthy = $false
$deadline = (Get-Date).AddSeconds(180)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    try {
        # 注意：PowerShell 5.1 的 Invoke-WebRequest 对非 text/* 响应会把 Content 返回成 byte[]，
        # 此时 `$r.Content -match 'UP'` 永远为 false（会误报"180 秒内未就绪"）。
        # 改用 Invoke-RestMethod直接拿已解析的对象。
        $h = Invoke-RestMethod -Uri "http://127.0.0.1:$AppPort/actuator/health" -TimeoutSec 5
        if ($h.status -eq 'UP') { $healthy = $true; break }
    } catch { }
    Write-Host '.' -NoNewline
}
Write-Host ''
if ($healthy) {
    $java = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
              Where-Object { $_.CommandLine -like '*oa-server*' } |
              Select-Object -ExpandProperty ProcessId)
    Save-Pid 'app-java' $java
    Write-Ok "应用已就绪：http://127.0.0.1:$AppPort/actuator/health -> UP"
    if ($java) { Write-Ok "java PID=$($java -join ',')" }
} else {
    Write-Warn "180 秒内未就绪，请查看日志尾部：$AppLog"
    Get-LogTail $AppLog 30
    exit 1
}

Write-Step "全部就绪"
Write-Host "  MySQL  : 127.0.0.1:$MySqlPort  (日志 $MySqlLog)"
Write-Host "  Redis  : 127.0.0.1:$RedisPort  (日志 $RedisLog)"
Write-Host "  应用   : http://127.0.0.1:$AppPort  (日志 $AppLog)"
Write-Host "  停止   : pwsh -File `"$Runtime\stop-local.ps1`""
