<#
================================================================================
 stop-local.ps1 —— 停止本机 OA 本地环境（应用 → Redis → MySQL）
--------------------------------------------------------------------------------
 用法：
   pwsh -File H:\dsh\OA\oa-deploy\runtime\stop-local.ps1              # 全停
   pwsh -File ...\stop-local.ps1 -OnlyApp                            # 只停应用
   pwsh -File ...\stop-local.ps1 -KeepData                           # 只停进程（默认也是只停进程）

 说明：
   - 应用：taskkill /T 杀 run-app.cmd 进程树（cmd → mvn → java），并兜底清理占用 8080 的进程。
   - Redis：优先 redis-cli shutdown nosave（优雅），失败则 taskkill。
   - MySQL：优先 mysqladmin -u root shutdown（优雅刷盘），失败则 taskkill。
   - 数据目录保留，不删除任何数据。
================================================================================
#>
[CmdletBinding()]
param(
    [switch]$OnlyApp,
    [switch]$OnlyServices
)

$ErrorActionPreference = 'Continue'

$Runtime = $PSScriptRoot
$Root    = Split-Path (Split-Path $Runtime -Parent) -Parent
$Cache   = Join-Path $Root '.cache'

$MySqlHome  = Join-Path $Cache 'mysql\extract\mysql-8.0.40-winx64'
$MySqlAdmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
$RedisHome  = Join-Path $Cache 'redis\redis-5.0.14.1'
$RedisCli   = Join-Path $RedisHome 'redis-cli.exe'

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok  ($msg) { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!]  $msg" -ForegroundColor Yellow }

function Get-SavedPid([string]$Name) {
    $f = Join-Path $Runtime "$Name.pid"
    if (-not (Test-Path $f)) { return @() }
    return @((Get-Content $f -Raw) -split ',' | ForEach-Object { $_.Trim() } |
             Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
}

function Stop-Tree([int]$ProcessId, [string]$What) {
    $p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $p) { Write-Warn "$What (PID $ProcessId) 未在运行"; return }
    & taskkill.exe /PID $ProcessId /T /F 2>&1 | Out-Null
    Write-Ok "$What (PID $ProcessId) 已停止（含子进程）"
}

function Stop-ByPort([int]$Port, [string]$What) {
    $lines = & netstat.exe -ano | Select-String ":$Port\s+.*LISTENING"
    foreach ($line in $lines) {
        # 注意：不要用 $pid（PowerShell 只读自动变量）
        $owner = [int](($line.Line -split '\s+')[-1])
        if ($owner -gt 0) { Stop-Tree $owner "$What(端口 $Port)" }
    }
}

# ------------------------------------------------------------------ 1) 应用
if (-not $OnlyServices) {
    Write-Step "停止 oa-server"
    foreach ($procId in (Get-SavedPid 'app'))      { Stop-Tree $procId 'oa-server(cmd)' }
    foreach ($procId in (Get-SavedPid 'app-java')) { Stop-Tree $procId 'oa-server(java)' }
    Stop-ByPort 8080 'oa-server'
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*oa-server*' } |
        ForEach-Object { Stop-Tree $_.ProcessId 'oa-server(java 兜底)' }
    Remove-Item (Join-Path $Runtime 'app.pid') -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $Runtime 'app-java.pid') -ErrorAction SilentlyContinue
}

if ($OnlyApp) { exit 0 }

# ------------------------------------------------------------------ 2) Redis
Write-Step "停止 Redis"
$redisAlive = $true
try {
    $r = & $RedisCli -h 127.0.0.1 -p 6379 ping 2>$null
    if ($r -notmatch 'PONG') { $redisAlive = $false }
} catch { $redisAlive = $false }

if ($redisAlive) {
    & $RedisCli -h 127.0.0.1 -p 6379 shutdown nosave 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    if (Get-Process redis-server -ErrorAction SilentlyContinue) {
        Get-Process redis-server | ForEach-Object { Stop-Tree $_.Id 'redis-server' }
    } else { Write-Ok 'Redis 已优雅关闭' }
} else {
    Write-Warn 'Redis 未在运行'
}
Get-Process redis-server -ErrorAction SilentlyContinue | ForEach-Object { Stop-Tree $_.Id 'redis-server' }
Remove-Item (Join-Path $Runtime 'redis.pid') -ErrorAction SilentlyContinue

# ------------------------------------------------------------------ 3) MySQL
Write-Step "停止 MySQL"
$myAlive = $false
try {
    $pong = & $MySqlAdmin -u root ping 2>$null
    if ($pong -match 'alive') { $myAlive = $true }
} catch { }

if ($myAlive) {
    & $MySqlAdmin -u root shutdown 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    if (Get-Process mysqld -ErrorAction SilentlyContinue) {
        Write-Warn '优雅关闭未生效，强制结束'
        Get-Process mysqld | ForEach-Object { Stop-Tree $_.Id 'mysqld' }
    } else { Write-Ok 'MySQL 已优雅关闭（数据已刷盘）' }
} else {
    Write-Warn 'MySQL 未在通过 TCP 响应，尝试清理残留进程'
}
Get-Process mysqld -ErrorAction SilentlyContinue | ForEach-Object { Stop-Tree $_.Id 'mysqld' }
Remove-Item (Join-Path $Runtime 'mysqld.pid') -ErrorAction SilentlyContinue

Write-Step "已全部停止（数据目录保留：$Runtime）"
