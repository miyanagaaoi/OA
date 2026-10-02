# 本机开发凭据模板（**不含真实口令**，可安全入库）
#
# 用法：
#   copy oa-deploy\runtime\local-secrets.example.ps1 oa-deploy\runtime\local-secrets.ps1
#   然后编辑 local-secrets.ps1 填入本机口令（该文件已被 .gitignore 忽略，不会入库）
#
# start-local.ps1 会先 dot-source local-secrets.ps1；取不到则回退到环境变量
# OA_DB_USERNAME / OA_DB_PASSWORD；两者都没有时脚本会直接报错退出（没有内置默认口令）。

$DbUser = 'oa'
$DbPass = '<在此填写本机 MySQL 口令>'

# 说明：本机开发库口令由 `mysqld --initialize-insecure` 后手工创建，建议用随机值：
#   $pwd = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
#   mysql -uroot --skip-password -e "ALTER USER 'oa'@'%' IDENTIFIED BY '$pwd';"
#   # 再把 $pwd 填到本文件（或写入环境变量）
