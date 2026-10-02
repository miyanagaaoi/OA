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
| 口令 | `Admin@12345` |
| 工号 | `10086`（水印「姓名 + 工号」用） |
| 角色 | `admin`（系统管理员，拥有全部 94 项权限） |

**这不是系统内置账号**：`V1~V4` 迁移只播种角色/权限/授权，**不播种任何用户**（PRD 与 import-spec 的口径是「首个管理员在初始化时创建、随机口令线下分发、首登强制改密」，避免把固定口令写进交付物）。本机这份数据由 `.cache/bootstrap-dev.sql` 手工灌入，**只存在于本地开发库**，不随仓库交付。

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

期望：`flyway_schema_history` 里 **V1~V4 全部 `success=1`**；`sys_role=9`、`sys_permission=94`、`sys_role_permission=374`。

## 5. 启动后自检清单（5 分钟）

| # | 命令 / 操作 | 期望 |
| --- | --- | --- |
| 1 | `curl http://127.0.0.1:8080/actuator/health` | `{"status":"UP"}` |
| 2 | `SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='oa';` | **4**（由启动时的 `ImmutableTriggerInitializer` 幂等创建） |
| 3 | `UPDATE sys_log SET action='x' WHERE id=1;` | **必须报错** `ERROR 1644 (45000): sys_log is append-only`（AC-20） |
| 4 | `DELETE FROM flow_signature WHERE id=1;` | **必须报错** `… flow_signature is append-only` |
| 5 | 未登录访问 `http://127.0.0.1:8080/api/v1/identity/orgs/tree` | **401** |
| 6 | 用 `admin` 登录前端 → 待我审批 / 组织架构 / 人员管理 / 角色与权限 | 四页均渲染**真实**数据（空列表是正常的：库里还没有单据） |

> 第 3 条需要库里 `sys_log` 存在 `id=1` 的行——**MySQL 触发器是逐行触发的，空表上 `WHERE id=1` 命中 0 行不会触发**，会「假通过」。可用 `.cache/trigger-test-fixture.sql` 造夹具。

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
