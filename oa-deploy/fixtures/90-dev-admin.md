# 90 · 开发管理员账号（**口令不入库**）

> 本目录**不提供**任何写死口令的 SQL。原因：PRD 与 `doc/import-spec.md` 的口径是
> 「首个管理员在初始化时创建、随机口令线下分发、首登强制改密」，**固定口令不得进入交付物**。
> 历史文件 `.cache/bootstrap-dev.sql` 就带着一个固定口令的 BCrypt 哈希，**没有原样入库**。
> 下面给的是「你自己生成、你自己插入」的可复制命令。

适用范围：**仅本机开发库**。生产/联调环境按 [`../env-checklist.md`](../env-checklist.md) 走初始化流程。

---

## 1. 先决条件

- MySQL 已起、库 `oa` 已由 Flyway 建好（`flyway_schema_history` 里 V1~V4 `success=1`）。
- `oa-deploy/fixtures/10-dev-orgs.sql` 已执行（`admin` 用户要挂到集团根节点 `id=1` 上）。
- 本机有 JDK 21 与 Maven（版本见仓库根 `README.md`）。

## 2. 生成 BCrypt 哈希（口令由你自定）

用项目**已有依赖** `spring-security-crypto` 的 `BCryptPasswordEncoder`（与
`com.oa.common.security.PasswordService` 同源），避免手写/网搜哈希造成口径不一致。

```powershell
# 0) 环境（按你本机实际路径调整）
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "C:\Tools\apache-maven-3.9.16\bin;$env:JAVA_HOME\bin;$env:Path"

# 1) 让 Maven 把 oa-server 的依赖 classpath 写到一个**仓库外**的临时文件（本仓库无聚合 pom，需 -f 指定模块）
mvn -q -f oa-server/pom.xml -DskipTests dependency:build-classpath "-Dmdep.outputFile=$env:TEMP\oa-fixture-cp.txt"

# 2) 把 classpath **读进变量**（-cp 收的是 classpath 字符串，不是文件路径）
$cp = Get-Content "$env:TEMP\oa-fixture-cp.txt" -Raw

# 3) 编译本目录自带的生成器（源码入库，不含任何口令）到临时目录
javac -cp $cp -d "$env:TEMP\oa-fixture-tools" oa-deploy/fixtures/tools/GenHash.java

# 4) 传你自己的口令，strength 用 12 与 PasswordService 对齐
java -cp "$env:TEMP\oa-fixture-tools;$cp" GenHash "<你的口令>" 12
```

输出形如：

```
strength = 12
hash     = $2a$12$....................（60 字符）
matches  = true
wrongPwd = false
```

> `matches = true` 是**自校验**：说明这个哈希确实对应你刚输入的口令。
> 生成器只打印哈希与自校验结果，**不回显口令、不落盘**。
> classpath 文件与 class 文件都写在 `$env:TEMP` 下，不会进入仓库。

## 3. 插入 / 更新 admin 用户

把上一步的哈希粘到下面 `SET @pwd_hash` 里执行（**不要**把这条带真实哈希的语句提交进仓库）：

```sql
SET NAMES utf8mb4;

SET @pwd_hash = '<粘贴你刚生成的哈希>';
SET @org_id   = (SELECT id FROM sys_org WHERE id = 1);   -- 集团根节点，由 10-dev-orgs.sql 创建

INSERT INTO sys_user (account, name, employee_no, password_hash, org_id, company_id, position, status, remark)
SELECT 'admin', '系统管理员', '10086', @pwd_hash, @org_id, @org_id, '系统管理员', 'active', '本机开发管理员（口令由使用者自定）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE account = 'admin');

-- 已存在则只改口令（幂等，可反复执行）
UPDATE sys_user
   SET password_hash = @pwd_hash,
       name          = '系统管理员',
       employee_no   = '10086',
       org_id        = @org_id,
       company_id    = @org_id,
       status        = 'active',
       deleted_at    = NULL
 WHERE account = 'admin';

-- 挂内置 admin 角色（拥有全部 94 项权限）
INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, '本机开发管理员'
  FROM sys_user u JOIN sys_role r ON r.code = 'admin'
 WHERE u.account = 'admin'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- 自检
SELECT u.id, u.account, u.name, u.employee_no, u.status,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id) AS admin_perms
  FROM sys_user u
  JOIN sys_role r ON r.code = 'admin'
  JOIN sys_user_role ur ON ur.user_id = u.id AND ur.role_id = r.id
 WHERE u.account = 'admin';
```

期望：`admin_perms = 94`。

## 4. 让演示账号也能登录（可选）

`20-dev-people.sql` / `40-authz-matrix.sql` 里所有账号的 `password_hash` 都是
**占位哈希**（`$2a$12$000…`，语法合法但不对应任何口令）——这是刻意的：
仓库里不出现任何可用口令。

需要以某个演示角色登录看数据域差异时，用第 2 步生成的哈希做一次 UPDATE 即可：

```sql
SET @pwd_hash = '<你生成的哈希>';
UPDATE sys_user SET password_hash = @pwd_hash WHERE account IN ('dev_ca01','dev_dl01','dev_gl01','dev_em01','dev_em02','dev_sc01','dev_fo01');
-- 矩阵账号同理
UPDATE sys_user SET password_hash = @pwd_hash WHERE account IN ('mtx_ca01','mtx_dl01','mtx_gl01','mtx_em01','mtx_em02');
```

## 5. 与 `oa-deploy/LOCAL-DEV.md` 的关系

`LOCAL-DEV.md` §2 记录的是**本机当前这份库**的实际账号状态；重置库之后它不再自动成立，
必须按本文件重新生成并插入。夹具执行顺序见 [`README.md`](README.md)。
