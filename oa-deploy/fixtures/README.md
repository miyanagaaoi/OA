# 开发夹具与演示数据（`oa-deploy/fixtures/`）

> 目的：**重置库之后，仓库里有一套脚本能把库恢复到「可演示、可跑集成测试」的状态。**
> 背景：此前这些数据只存在于 `.cache/*.sql`，而 `.cache/` 被 `.gitignore` 忽略 ——
> 新环境 `git clone` + 重置库后**除了迁移种子什么都没有**，无法演示；
> 上一轮重置库还把 `RT-*`（运行期造的组织）这类数据永久弄丢了，因为仓库里没有任何脚本定义它们。
> 本目录把它们固化成**入库、幂等、可复现**的脚本。

---

## 1. 执行顺序（不可调换）

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 0 | `oa-server` 启动（Flyway 自动跑 `V1__schema` → `V2__dict_seed` → `V3__templates` → `V4__permissions`） | 建表 + 播种角色 / 权限 / 字典 / 流程模板（**不含任何组织与人员**） |
| 1 | [`10-dev-orgs.sql`](10-dev-orgs.sql) | 四级演示组织（集团 → 2 家公司 → 部门 → 科室）+ 集团财务部 + 被弄丢的 `RT-*` 样例组织 |
| 2 | [`20-dev-people.sql`](20-dev-people.sql) | 演示人员（各角色样本）+ 一人多岗 + **负责人链**（四类单据 precheck 跑到 `allowed=true` 的必要条件） |
| 3 | [`30-dev-roles.sql`](30-dev-roles.sql) | 演示账号 → 9 个内置角色（覆盖矩阵测试需要的 `company_admin` / `dept_leader` / `group_leader` / `employee`） |
| 4 | [`40-authz-matrix.sql`](40-authz-matrix.sql) | 越权矩阵固定夹具（`mtx_*` 账号 + 四级组织链 + **`sys_user.id=1` 的系统管理员占位行**） |
| 5 | [`50-trigger-fixture.sql`](50-trigger-fixture.sql) | AC-20 不可篡改触发器验收夹具（`sys_log` / `flow_signature` 的 id=1 行） |
| 6 | [`99-verify.sql`](99-verify.sql) | **只读**断言：关键行数逐条 PASS/FAIL |
| — | [`90-dev-admin.md`](90-dev-admin.md) | **不是 SQL**：如何生成 BCrypt 哈希并插入 `admin`（口令由使用者自定，**仓库里不出现任何可用口令**） |

> 顺序依赖：`20` 依赖 `10`（外键 `sys_user.org_id`）；`50` 依赖 `10`/`20`（`form_data.creator_id` 非空外键）；
> `30`/`40` 依赖 Flyway V4 的角色。
> `90` 是文档，任何时候执行都行；但**先执行 `10`**，否则 `admin` 挂不到集团根节点上。

## 2. 一键执行（PowerShell）

```powershell
# 口令从本机密钥文件读，不写进命令行之外的地方；也不回显
$txt = Get-Content oa-deploy\runtime\local-secrets.ps1 -Raw
$dbu = [regex]::Match($txt, "\`$DbUser\s*=\s*'([^']*)'").Groups[1].Value
$dbp = [regex]::Match($txt, "\`$DbPass\s*=\s*'([^']*)'").Groups[1].Value
$mysql = 'H:\dsh\OA\.cache\mysql\extract\mysql-8.0.40-winx64\bin\mysql.exe'   # 或 PATH 里的 mysql

foreach ($f in '10-dev-orgs.sql','20-dev-people.sql','30-dev-roles.sql','40-authz-matrix.sql','50-trigger-fixture.sql','99-verify.sql') {
  "### $f"
  & $mysql --host=127.0.0.1 --port=3306 "-u$dbu" "-p$dbp" -D oa --default-character-set=utf8mb4 `
           -e "source oa-deploy/fixtures/$f" 2>$null
}
```

> `local-secrets.ps1` 在 `oa-deploy/runtime/`（该目录已 gitignore）。
> 若本机 PowerShell 执行策略禁止运行脚本，**不要** dot-source，用上面的 `Get-Content` + 正则读值方式。
> 直接用 `mysql` 交互式终端时，等价写法是 `SOURCE oa-deploy/fixtures/10-dev-orgs.sql;`（路径用 `/`）。

## 3. 幂等性说明

所有脚本都**可反复执行**：重复执行不新增行、不改变 id、不覆盖你手工设置的口令。

| 脚本 | 幂等写法 | 依赖的唯一键 |
| --- | --- | --- |
| `10-dev-orgs.sql` | 显式主键 + `INSERT ... ON DUPLICATE KEY UPDATE` | `sys_org.PRIMARY KEY (id)`、`uk_sys_org_path (path)` |
| `20-dev-people.sql` | 同上 + `INSERT ... ON DUPLICATE KEY UPDATE`（**刻意不更新 `password_hash`**）；子行按 `account` 反查 `user_id`；负责人链为**直接 upsert** | `sys_user.PRIMARY KEY (id)`、`uk_sys_user_account (account)`；`sys_user_position.uk_user_org (user_id, org_id)`；`sys_org_leader.uk_org_leader (org_id, user_id, leader_type, category_key)`（`category_key` 是 `IFNULL(category,'')` 的**生成列**，NULL 与 NULL 视为同一组 → 可安全 upsert） |
| `30-dev-roles.sql` | `INSERT ... SELECT ... WHERE r.code = ?` + `ON DUPLICATE KEY UPDATE` | `sys_user_role.uk_sys_user_role (user_id, role_id, scope_org_key)`（`scope_org_id` 为 NULL 时按 0 参与） |
| `40-authz-matrix.sql` | 与 `10`/`20`/`30` 相同的显式主键 + `ON DUPLICATE KEY UPDATE`；`matrix_admin`（`sys_user.id=1`）用 `INSERT ... SELECT ... WHERE NOT EXISTS` **只补空缺** | 同上 + `sys_user.PRIMARY KEY (id)` |
| `50-trigger-fixture.sql` | `INSERT ... SELECT ... WHERE NOT EXISTS (...)` | `sys_log.id`、`form_data.uk_form_data_biz_no`、`flow_instance.uk_flow_instance_biz_no`、`flow_signature.id` |

**保留 id 段**（本目录约定，其它种子/脚本请避开）：

| 段 | 用途 |
| --- | --- |
| `1` | 集团根节点（`sys_org`）；**`sys_user.id=1` 由 `40-authz-matrix.sql` 占位**（`matrix_admin`） |
| `12` / `13` | 公司A / 公司B |
| `135`–`138` | 部门1 / 部门2 / 部门3 / 科室1 |
| `150` | 集团财务部 |
| `151`–`155` | `RT-*` 运行期样例组织 |
| `201`–`205` | 矩阵人员（`mtx_*`）—— **`40-authz-matrix.sql` 独占** |
| `301`–`307` | 演示人员（`dev_*`）—— **`20-dev-people.sql` 独占** |

> 两套人员 id 段**互不重叠**，`20` 与 `40` 可同时执行、互不覆盖（早期版本曾共用 201–205，导致
> 一个脚本把另一个的账号改名，已拆开）。子行一律**按 `account` 反查真实 `user_id`**，
> 因此即使库里某账号的 id 与预期不同，也不会产生外键悬空。
>
> ⚠️ **`sys_user.id=1` 是集成测试的硬依赖**：`DataScopeMySqlIntegrationTest`
> （`oa-server/src/test/java/com/oa/common/scope/DataScopeMySqlIntegrationTest.java:114`）与
> `AuthzMatrixMySqlIntegrationTest`（`:171`、`:222`）把**合成的当前登录人**写成 `userId = 1`，
> 要求库里真实存在 id=1 的行。旧环境里 id=1 恰好是最早插入的 bootstrap admin，于是长期「假绿」；
> 重置库后夹具不再提供该行 → 这两个用例立刻变红。故由 `40-authz-matrix.sql` 用
> `matrix_admin`（占位哈希、不可登录）**确定性地占住 id=1**；若 id=1 已被占用（例如你手工建的
> 真实 admin 就在 id=1），该语句整条跳过，绝不覆盖既有行的账号与口令。
>
> ⚠️ **`sys_org_leader` 的归属约定**：`org_id ∈ {1, 12, 135, 138, 150}` 的负责人行由
> `20-dev-people.sql` 独占（其它脚本/手工操作请挂到别的组织节点上）。
> 唯一键已是 `uk_org_leader (org_id, user_id, leader_type, category_key)`，其中 `category_key`
> 是 `IFNULL(category,'')` 的**生成列**（`doc/data-model.md` §2.3）—— 这修掉了「唯一键含可空列
> `category`、MySQL 对 NULL 不去重 → 重复执行不断堆积重复负责人行」的缺陷，
> 因此负责人链现在是**直接 upsert**，不再需要「归一重复 → 清空 → 重建」的特例。

## 4. 重置库之后如何恢复到「可演示」

```powershell
# 1) 重置库（Flyway 会在应用启动时重建 schema 与种子）
mysql -uoa -p -e "DROP DATABASE IF EXISTS oa; CREATE DATABASE oa DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2) 启动应用，等 /actuator/health 返回 UP（Flyway 跑完 V1~V4）
oa-deploy\runtime\start-local.cmd

# 3) 装载夹具（§2 的一键脚本）；然后按 90-dev-admin.md 建一个有口令的 admin

# 4) 断言：99-verify.sql 全部 PASS
```

期望（与 `oa-deploy/LOCAL-DEV.md` §5 的自检清单一致）：

- `sys_role = 9`、`sys_permission = 94`、`sys_role_permission = 372`、`flow_template = 4`
- `sys_org ≥ 13`（含 5 个 `RT-*`）、`sys_user.dev_* = 7`、`sys_user.mtx_* = 5`
- `sys_org_leader` 上 ①–⑥ 全部可解析 ⇒ 四类单据 precheck `allowed=true`
- `information_schema.TRIGGERS`（schema `oa`）**= 4**，且 `UPDATE sys_log WHERE id=1` 报错

## 5. 与 `.cache/` 的关系

`.cache/` 仍在 `.gitignore` 里，本目录是它的**入库替身**：

| 原 `.cache/` 文件 | 现入库位置 |
| --- | --- |
| `bootstrap-dev.sql` | **有意不移植**（含固定口令哈希）→ 改为 [`90-dev-admin.md`](90-dev-admin.md) |
| `oa-authz-matrix-fixture.sql` | [`40-authz-matrix.sql`](40-authz-matrix.sql) |
| `trigger-test-fixture.sql` | [`50-trigger-fixture.sql`](50-trigger-fixture.sql) |
| `flow-leader-chain-fixture.sql` | 并入 [`20-dev-people.sql`](20-dev-people.sql)（负责人链段） |
| `GenHash.java` | [`tools/GenHash.java`](tools/GenHash.java) |

### 5.1 集成测试**默认**就读本目录（已收口）

两个矩阵测试的夹具**默认路径已指向本目录**（`oa-server/**` 已改，见
`AuthzMatrixFixture`），因此新环境 `git clone` 后直接跑集成测试即可，无需再手工传路径：

| 测试 | 默认路径 | 覆盖方式 |
| --- | --- | --- |
| `AuthzMatrixHttpTest` | `../oa-deploy/fixtures/40-authz-matrix.sql`（相对 Maven `${basedir}` = `oa-server/`） | 系统属性 `-Doa.it.fixture=<路径>` 或环境变量 `OA_IT_FIXTURE` |
| `AuthzMatrixMySqlIntegrationTest` | 同上（共用 `AuthzMatrixFixture.resolve`） | 同上 |

**语义收紧（防「假绿」）**：完全没提供 DB 环境（`-Doa.it.db.url` / `OA_IT_DB_URL` 为空）→ 整类跳过（合法）；
**已提供 DB 环境却找不到夹具 → 直接失败**（`IllegalStateException`，报错里给出可复制的
`-Doa.it.fixture=oa-deploy\fixtures\40-authz-matrix.sql` 提示），不再 `assumeTrue` 静默跳过。

```powershell
# 例：用入库夹具跑 HTTP 矩阵（其余 OA_IT_* 见测试类注释；夹具路径已可省略）
mvn -f oa-server/pom.xml -B test "-Dtest=AuthzMatrixHttpTest"
```

> 本目录的 `40-authz-matrix.sql` 与该测试的解析方式兼容：只用 `--` 行注释、`;` 结尾，
> 测试的 `stripComments` + 按 `;` 切分可直接执行。
> 触发器夹具 `50-trigger-fixture.sql` **没有任何测试引用**（它是 `LOCAL-DEV.md` §5 第 3/4 条
> 手工验收的前提），故不涉及默认路径改造。

## 6. 安全边界

- 本目录**不含任何真实凭据**：没有明文口令、没有可用口令的哈希、没有密钥。
  所有 `password_hash` 都是占位哈希（`$2a$12$000…`），**不对应任何口令**。
- **为什么占位哈希「不可登录」是可证明的**：它是 `$2a$12$` + **52** 个 `0`，合计 **59 字符**，
  而 BCrypt 密文恒为 **60 字符**（`$2a$12$` + 22 位 salt + 31 位 digest）。Spring Security 的
  `BCryptPasswordEncoder#matches` 遇到非 60 字符的密文会打印
  `Encoded password does not look like BCrypt` 并**对任何口令一律返回 false**
  —— 即**结构上无法参与校验**，不依赖「猜不到口令」这一较弱假设。
  回归网：`oa-server/src/test/java/com/oa/platform/fixture/FixtureCredentialSafetyTest.java`
  （① 逐字断言夹具里每个 BCrypt 字面量都等于该占位值，任何「看起来可用」的哈希直接变红；
  ② 断言它非 60 字符 ⇒ 对任意口令 `matches=false`；③ 提供 `-Doa.it.known.passwords` 时
  对候选口令逐个断言 `matches=false`）。
  运行期实测（本机）：以已知 dev 口令 POST `/api/v1/auth/login` 打 `mtx_em01` / `mtx_dl01` /
  `mtx_gl01` / `mtx_em02` / `matrix_admin` 全部 `401 40103 账号或口令错误`；
  同一入口在把口令运行期 `UPDATE` 进 `mtx_ca01` 后返回 `200`（证明 401 是哈希不可用，不是入口坏了）。
- **需要登录时怎么注入**：口令**只**在运行期给出，绝不写进本目录 ——
  `AuthzMatrixHttpTest#applyFixtureAndCredentials` 会在 `@BeforeAll` 里重放本目录夹具，
  再用 `PasswordService` 为 `mtx_*` 现生成**随机口令**并 `UPDATE sys_user.password_hash`。
- 手机号 `138…` 是**示例号段**，非真实号码。
- 这些脚本只应在本机开发库执行；生产/联调按 [`../env-checklist.md`](../env-checklist.md) 走。
