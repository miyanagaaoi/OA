# 本机开发环境（Windows，无 Docker 也能跑）

> 目的：让新同事在一台干净的 Windows 机器上，**不装 Docker、不需要管理员权限**，把「MySQL + Redis + 后端 + 前端」整套跑起来并打开页面。
> 适用：开发/演示自测。**生产部署请走 [`docker-compose.yml`](docker-compose.yml) + [`nginx/oa.conf`](nginx/oa.conf)**（见 [`env-checklist.md`](env-checklist.md)）。

---

## 1. 一键启停

```powershell
# 启动（幂等：端口已在监听的服务会被跳过）
oa-deploy\runtime\start-local.cmd

# 停止（应用强杀进程树；MySQL/Redis 走优雅关闭，数据目录保留）
oa-deploy\runtime\stop-local.cmd
```

> 脚本本体在 `oa-deploy/runtime/`（**该目录已 gitignore**，含数据目录与日志，不入库）。
> 若本机 PowerShell 执行策略为 Restricted，用上面的 `.cmd` 包装（内部以 `-ExecutionPolicy Bypass` 调 `.ps1`）。

启动完成后访问：

| 入口 | 地址 |
| --- | --- |
| **前端** | <http://127.0.0.1:5273/> |
| 后端健康检查 | <http://127.0.0.1:8080/actuator/health> |
| MySQL / Redis | `127.0.0.1:3306` / `127.0.0.1:6379` |

## 2. 开发用管理员账号（**仅本机开发库**）

| 项 | 值 |
| --- | --- |
| 账号 | `admin` |
| 口令 | **由你自定**（旧的固定开发口令已随提交 `0448683`「开发口令移出仓库 + 轮换已泄露口令」废弃，本文不再记录任何口令字面量） |
| 工号 | `10086`（水印「姓名 + 工号」用） |
| 角色 | `admin`（系统管理员，拥有全部 94 项权限） |

**这不是系统内置账号**：`V1~V4` 迁移只播种角色/权限/授权，**不播种任何用户**（PRD 与 import-spec 的口径是「首个管理员在初始化时创建、随机口令线下分发、首登强制改密」，避免把固定口令写进交付物）。仓库里也**不存放任何可用口令的哈希**——本机这份数据由你自己生成哈希后手工灌入，见 §2.1。

### 2.1 重建管理员账号（重置库后必做）

完整步骤（含 BCrypt 哈希生成器与可复制的 SQL）见 **[`fixtures/90-dev-admin.md`](fixtures/90-dev-admin.md)**。摘要：

```powershell
# 1) 生成哈希（口令由你自定；生成器源码在库内，不含任何口令）
mvn -q -f oa-server/pom.xml -DskipTests dependency:build-classpath "-Dmdep.outputFile=$env:TEMP\oa-fixture-cp.txt"
$cp = Get-Content "$env:TEMP\oa-fixture-cp.txt" -Raw
javac -cp $cp -d "$env:TEMP\oa-fixture-tools" oa-deploy/fixtures/tools/GenHash.java
java -cp "$env:TEMP\oa-fixture-tools;$cp" GenHash "<你的口令>" 12
# 2) 把输出的 hash 粘进 90-dev-admin.md 第 3 节的 SQL 并执行
```

## 3. 依赖从哪来（全部便携版，未注册 Windows 服务）

| 组件 | 来源 | 落点 |
| --- | --- | --- |
| MySQL 8.0.40 | `https://cdn.mysql.com/archives/mysql-8.0/mysql-8.0.40-winx64.zip`（dev.mysql.com 的 8.0.40 已 404，用 archives 路径） | `.cache/mysql/extract/…`，数据目录 `oa-deploy/runtime/mysql/data`，参数见 `oa-deploy/runtime/mysql/my.ini` |
| Redis 5.0.14.1 (Windows) | `https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip` | `.cache/`，运行数据 `oa-deploy/runtime/redis/` |

MySQL 关键参数与 `docker-compose.yml` 对齐：`utf8mb4` / `utf8mb4_general_ci` / `+08:00` / `log-bin-trust-function-creators=1` / `max_connections=300`。

## 4. 首次建库（应用自己跑 Flyway，**不要手工灌历史**）

```powershell
# 清库重来（可选）
mysql -uoa -p -e "DROP DATABASE IF EXISTS oa; CREATE DATABASE oa DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
# 启动应用后由 Flyway 自动执行 V1 → V2 → V3 → V4
```

期望：`flyway_schema_history` 里 **V1~V4 全部 `success=1`**；`sys_role=9`、`sys_permission=94`、`sys_role_permission=376`。

> **生成产物是确定性的，可安全重跑**：`tools/gen-init-sql.js` 与 `tools/build-flyway-migrations.js` 的产物头部只有
> 确定性溯源行（生成器自身 sha256 + 来源内容 sha256），**不含墙钟时间戳**，因此「同一输入 → 逐字节相同」，
> 重跑生成器不会改变 `V1~V4` 的字节，也就不会引起 Flyway checksum 漂移。
> 但**一旦产物内容真的变了**（改了文档 / 改了迁移），已建库上的 checksum 与历史不一致，
> `validate-on-migrate: true` 下启动会报 `Migration checksum mismatch` —— 此时**需重置库**（重跑上面的 DROP/CREATE + 引导数据），
> 或对可接受的本地库执行 `flyway repair` 重写历史 checksum。

## 4.1 夹具 / 演示数据（**入库、可复现**）

Flyway 的 `V1~V4` **只播种角色 / 权限 / 字典 / 流程模板，不播种任何组织与人员**。
重置库之后，**组织树是空的、四类单据因候选人为空而 precheck 失败、`RT-*` 这类运行期样例也会消失**。
为此仓库提供 [`oa-deploy/fixtures/`](fixtures/README.md)：一套**幂等、可反复执行**的脚本，
把库恢复到「可演示、可跑集成测试」的状态。**重置库后不要再依赖 `.cache/*.sql`（已 gitignore，clone 不到）。**

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | [`fixtures/10-dev-orgs.sql`](fixtures/10-dev-orgs.sql) | 四级演示组织（集团 → 2 家公司 → 部门 → 科室）+ 集团财务部 + 被弄丢的 `RT-*` 样例组织 |
| 2 | [`fixtures/20-dev-people.sql`](fixtures/20-dev-people.sql) | 演示人员 + 一人多岗 + **负责人链** ⇒ 四类单据 precheck 跑到 `allowed=true` |
| 3 | [`fixtures/30-dev-roles.sql`](fixtures/30-dev-roles.sql) | 演示账号 → 9 个内置角色（覆盖 `company_admin`/`dept_leader`/`group_leader`/`employee`） |
| 4 | [`fixtures/40-authz-matrix.sql`](fixtures/40-authz-matrix.sql) | 越权矩阵固定夹具（`mtx_*` 账号 + 四级组织链 + **`sys_user.id=1` 的系统管理员占位行**，两个数据域集成测试依赖它） |
| 5 | [`fixtures/50-trigger-fixture.sql`](fixtures/50-trigger-fixture.sql) | AC-20 触发器验收所需的 `sys_log` / `flow_signature` 行（§5 第 3/4 条的前提） |
| 6 | [`fixtures/99-verify.sql`](fixtures/99-verify.sql) | **只读断言**：关键行数逐条 `PASS`/`FAIL` |
| — | [`fixtures/90-dev-admin.md`](fixtures/90-dev-admin.md) | 生成 BCrypt 哈希并插入 `admin`（**口令由你自定**，仓库不含任何可用口令） |

一键执行（口令从 `local-secrets.ps1` 读，不回显；未 dot-source，避免执行策略限制）：

```powershell
$txt = Get-Content oa-deploy\runtime\local-secrets.ps1 -Raw
$dbu = [regex]::Match($txt, "\`$DbUser\s*=\s*'([^']*)'").Groups[1].Value
$dbp = [regex]::Match($txt, "\`$DbPass\s*=\s*'([^']*)'").Groups[1].Value
$mysql = 'H:\dsh\OA\.cache\mysql\extract\mysql-8.0.40-winx64\bin\mysql.exe'
foreach ($f in '10-dev-orgs.sql','20-dev-people.sql','30-dev-roles.sql','40-authz-matrix.sql','50-trigger-fixture.sql','99-verify.sql') {
  & $mysql --host=127.0.0.1 --port=3306 "-u$dbu" "-p$dbp" -D oa --default-character-set=utf8mb4 -e "source oa-deploy/fixtures/$f" 2>$null
}
```

**重置库 → 恢复可演示状态**（完整版见 [`fixtures/README.md`](fixtures/README.md) §4）：

```powershell
mysql -uoa -p -e "DROP DATABASE IF EXISTS oa; CREATE DATABASE oa DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
oa-deploy\runtime\start-local.cmd          # 等 /actuator/health 返回 UP（Flyway 跑完 V1~V4）
# 执行上面的夹具脚本；再按 fixtures/90-dev-admin.md 建一个有口令的 admin
# 最后 fixtures/99-verify.sql 应 16/16 PASS
```

> **幂等**：所有夹具脚本都是显式主键 + upsert，**反复执行不产生重复数据**。
> `sys_org_leader` 此前是例外（唯一键含可空列 `category`，MySQL 对 NULL 不去重），现已在真源
> `doc/data-model.md` §2.3 修掉：`category_key GENERATED ALWAYS AS (IFNULL(category,'')) STORED`
> 纳入唯一键 `uk_org_leader (org_id, user_id, leader_type, category_key)`，负责人链因此也改回**直接 upsert**
> （飞轮路径：改 `doc/data-model.md` → `node tools/gen-init-sql.js` → `node tools/build-flyway-migrations.js` → 重置库）。
> 已在临时库上从零验证：同一套迁移 + 夹具 + 断言 16/16 PASS，重复执行行数不变（连跑两次 `sys_org_leader` 行数一致）。

## 5. 启动后自检清单（5 分钟）

| # | 命令 / 操作 | 期望 |
| --- | --- | --- |
| 1 | `curl http://127.0.0.1:8080/actuator/health` | `{"status":"UP"}` |
| 2 | `SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='oa';` | **4**（由启动时的 `ImmutableTriggerInitializer` 幂等创建） |
| 3 | `UPDATE sys_log SET action='x' WHERE id=1;` | **必须报错** `ERROR 1644 (45000): sys_log is append-only`（AC-20） |
| 4 | `DELETE FROM flow_signature WHERE id=1;` | **必须报错** `… flow_signature is append-only` |
| 5 | 未登录访问 `http://127.0.0.1:8080/api/v1/identity/orgs/tree` | **401** |
| 6 | 用 `admin` 登录前端 → 待我审批 / 组织架构 / 人员管理 / 角色与权限 | 四页均渲染**真实**数据（空列表是正常的：库里还没有单据） |

> 第 3 条需要库里 `sys_log` 存在 `id=1` 的行——**MySQL 触发器是逐行触发的，空表上 `WHERE id=1` 命中 0 行不会触发**，会「假通过」。
> 用 [`fixtures/50-trigger-fixture.sql`](fixtures/50-trigger-fixture.sql) 造夹具（已入库，替代原 `.cache/trigger-test-fixture.sql`）。

> **文档侧只读自检（不需要起数据库）**：`node tools/check-normify-anchors.js --check` —— 校验结构基线 `normify-oa` 里 doc 证据引用「锚点化」是否成立（doc 引用不得带行号、每条 doc 引用在模块正文 `## 证据锚点` 有对应锚点、锚点必须真的存在于该文档）；有 error 时 exit 1，已接入 `docs-ci.yml`。工具清单见 [`../doc/dependencies.md`](../doc/dependencies.md) §5。

## 6. 已知限制（本机开发形态）

- 前端默认**关掉演示数据降级**（`oa-web/.env.development.local` 内 `VITE_USE_MOCK=false`，该文件已 gitignore）。想只跑前端看界面，把它改成 `true` 即可。
- 尚未实现的接口（`/portal/workbench/*`、`/notifications/*`、流程类接口等）返回 **404**（阶段 2/3/4 才做），控制台会有提示——这是**预期**，不是故障。
- **在途/待办检查仍是桩（恒 0）**：`DefaultInFlightChecker` 未接流程表，因此 AC-11/AC-12 的「停用/离职前必须清空」目前不会真正拦截，force（强制继续）也走桩；接流程表后自动生效（接入点写在类注释里）。
- 手机号 AES-256-GCM 加密未接入（骨架直存 + 出参脱敏）。
- 本机形态**仅用于开发**：HTTPS、CSP、HSTS 由生产 Nginx 承担；dev profile 关闭了 Secure Cookie（因为本机是 http）。

## 7. 排障：三类「静态检查全绿但跑不起来」的历史坑（已修，勿回退）

| 症状 | 根因 |
| --- | --- |
| Flyway 在 V2 报 `1060 Duplicate column name` | 种子脚本与建表语句重复加列（文档漂移）。**改文档后必须重跑** `node tools/gen-init-sql.js` 与 `node tools/build-flyway-migrations.js`，其自检会拦住这类漂移 |
| 启动即 `circular reference`（`allow-circular-references=true` 也无效） | 纯构造器注入的环：`WebMvcConfig ↔ AuthInterceptor ↔ ObjectMapper`。Jackson customizer 必须放在 `common/config/JacksonConfig`，**不要搬回 `WebMvcConfig`** |
| 触发器只建成 3/4，`UPDATE sys_log` 能成功 | 触发器脚本注释里出现**字面量双斜杠**被当作分隔符，吞掉了一条 `CREATE TRIGGER`。解析器已改为**行锚定切分 + 条数自检**（不一致直接拒绝启动）；生成器注释也已改写 |
| 浏览器登录 `403 Invalid CORS request`，而 curl 正常 | dev 允许来源与 Vite 实际端口不一致（历史：允许 5173，实际 5273）。`application-dev.yml` 的 `oa.web.allowed-origins` 必须与 `oa-web/vite.config.ts` 的 `server.port` 对齐；Vite 代理同时把 `Origin` 改写为后端自身来源（与生产同源反代一致） |
| 接口 500 且日志 `No value specified for parameter 1` | 数据域拦截器织入带参片段后**丢掉了原有参数映射**。现实现为「在原 BoundSql 上原位插入 `Mode.IN` 映射」，并有 `DataScopeParameterBindingTest` 等 11 条测试守着 |
| 删除 Flyway 迁移后应用仍报 `1060 Duplicate column name` | Maven **不会清理** `oa-server/target/classes/db/migration/` 下的陈旧副本，而 `spring-boot:run` 用的正是 `target/classes` → **新旧迁移混跑**（被删的迁移会继续执行）。删迁移后必须 `mvn clean`，或手工删掉该目录下的副本 |
| 重跑生成器后启动报 `Migration checksum mismatch` | 产物内容变了（文档/迁移/生成器改动）→ 已建库里的历史 checksum 失效。生成产物本身**确定性可安全重跑**（无时间戳）；内容真变时按 §4 重置库，或本地库执行 `flyway repair` |
| 同父节点下重名组织被允许（仅日志提示，不阻断）→ 期望「重名被拒」是**误解** | **这是预期行为**：唯一性由 `sys_org.path`（id 路径 `/1/12/135/`）保证；业务键 `org_path`（名称路径）在同父同名时**只告警不阻断**（[`import-spec.md`](../doc/import-spec.md) §3.2、`OrgService#warnSiblingName`）。判重请看 `path`，不要看 `name` |
