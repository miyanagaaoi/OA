# 本机开发凭据模板（不含真实值，可安全入库）
#
# 用法：
#   copy oa-deploy\runtime\local-secrets.example.ps1 oa-deploy\runtime\local-secrets.ps1
#   然后编辑 local-secrets.ps1 填入本机值（该文件已被 .gitignore 忽略，不会入库）
#
# start-local.ps1 会先 dot-source local-secrets.ps1；取不到则回退到环境变量
# OA_DB_USERNAME / OA_DB_PASSWORD / OA_PHONE_KEY；都没有时脚本直接报错退出（无内置默认值）。
#
# 注意：三个值都必须是**单行**。

$DbUser = 'oa'
$DbPass = '<本机 MySQL 口令，24 位随机>'

# 手机号 AES-256-GCM 密钥（1.7）：应用缺该密钥会 fail-fast 拒绝启动。
# 生成：$k = -join (1..32 | ForEach-Object { (Get-Random -Max 256).ToString('x2') })
$PhoneKey = '<32 字节 hex 或 base64，单行>'
