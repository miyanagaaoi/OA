# oa-server · 集团OA审批系统后端（阶段 1 开工骨架）

> 状态：**阶段 1 工程骨架**（可编译、可跑纯逻辑单测、可有条件启动），**不是完整业务实现**。
> 依据：[`../doc/tech-design.md`](../doc/tech-design.md) V1.0 §2/§3.1/§4.1/§5.3/§5.4/§5.5/§6、
> [`../doc/data-model.md`](../doc/data-model.md)（**DDL 为准**）、[`../doc/prd-0.1.md`](../doc/prd-0.1.md) §5.3/§9.1。

---

## 1. 技术栈（已定稿，不得更改）

| 项 | 选型 |
| --- | --- |
| 语言/运行时 | Java 21（LTS） |
| 框架 | Spring Boot 3.2.12（web / validation / data-redis / aop / actuator） |
| ORM | MyBatis-Plus 3.5.7（`mybatis-plus-spring-boot3-starter`，**不用 JPA**） |
| 数据库 | MySQL 8（`com.mysql:mysql-connector-j`） |
| 缓存/会话 | Redis 7（`spring-boot-starter-data-redis` + Lettuce 连接池） |
| 迁移 | Flyway（`flyway-core` + `flyway-mysql`） |
| 口令 | `spring-security-crypto` 的 `BCryptPasswordEncoder`（strength 12）；**不引入完整 spring-boot-starter-security** |
| 构建 | Maven |

---

## 2. 本机准备（无 JDK / 无 Maven 时）

```powershell
# 1) 安装（示例，winget）
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven          # 或解压 apache-maven-3.9.x 到 C:\Tools

# 2) 每个新终端里显式设置（PowerShell 每个进程独立）
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path="C:\Tools\apache-maven-3.9.16\bin;$env:JAVA_HOME\bin;$env:Path"
java -version      # 期望 21.x
mvn -v             # 期望 Maven 3.9.x + Java 21
```

**Maven 走代理**（私有化环境/公司网络）——`~/.m2/settings.xml`（Windows：`C:\Users\<你>\.m2\settings.xml`）：

```xml
<settings>
  <proxies>
    <proxy>
      <id>http</id><active>true</active><protocol>http</protocol>
      <host>127.0.0.1</host><port>7899</port>
    </proxy>
    <proxy>
      <id>https</id><active>true</active><protocol>https</protocol>
      <host>127.0.0.1</host><port>7899</port>
    </proxy>
  </proxies>
</settings>
```

等价做法（不改 settings.xml）：

```powershell
$env:MAVEN_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7899 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7899"
```

---

## 3. 编译与测试

```powershell
cd H:\dsh\OA\oa-server
mvn -B -DskipTests compile     # 编译
mvn -B test                    # 纯逻辑单测（不启动 Spring 容器、不连 MySQL/Redis）
mvn -B -DskipTests package     # 打可执行 jar：target/oa-server-0.1.0-SNAPSHOT.jar
```

单测覆盖：

| 测试 | 断言内容 |
| --- | --- |
| `DataScopeSqlBuilderTest` | 5 种数据域 + 财务部五路并集（含「不涉及费用且未流转的事项单不可见」红线）、参数绑定、整体加括号、别名防注入、缺参数时 `1=0` 拒绝全表 |
| `LoginAttemptGuardTest` | 失败 5 次锁 15 分钟、锁定期内拒绝、到期自动解锁、窗口过期清零、账号归一化 |
| `FormWritePolicyTest` | 三态白名单 + 印鉴单唯一例外 + 完结只读 + 载荷过滤 |

---

## 4. 依赖服务与启动

```powershell
# MySQL 8（库名 oa），首次执行迁移：
mysql -u root -p -e "CREATE DATABASE oa DEFAULT CHARSET utf8mb4;"

# Redis 7（默认 127.0.0.1:6379）

# 启动（dev profile：CORS 开、Cookie Secure 关、SQL 日志开）
mvn -B spring-boot:run
# 或
java -jar target\oa-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

- 健康检查：`GET http://127.0.0.1:8080/actuator/health`
- 口令策略：`GET http://127.0.0.1:8080/api/v1/auth/password-policy`

关键环境变量：`OA_DB_USERNAME` / `OA_DB_PASSWORD` / `OA_REDIS_HOST` / `OA_REDIS_PORT` / `OA_REDIS_PASSWORD`。

### 4.1 Flyway

`src/main/resources/db/migration/` 由主控生成，**本工程不修改**：

| 文件 | 内容 |
| --- | --- |
| `V1__schema.sql` | 27 张表（`oa-deploy/sql/01-schema.sql` 去掉 `DELIMITER` 触发器段） |
| `V2__dict_seed.sql` | 8 类字典种子（幂等 INSERT） |
| `V3__templates.sql` | 四类单据流程模板（7 节点） |

`application.yml` 中的 Flyway 配置：`locations=classpath:db/migration`、**`encoding=UTF-8`**（脚本含中文注释，必须显式声明）、
`baseline-on-migrate=true`（`baseline-version=0`）、`sql-migration-separator=__`、`validate-on-migrate=true`、
`placeholder-replacement=false`（SQL 里的 `${...}` 不被替换）、`clean-disabled=true`。

### 4.2 不可篡改触发器（`ImmutableTriggerInitializer`）

触发器**不在** Flyway 迁移里（Flyway 不识别 mysql 客户端的 `DELIMITER`），由
`com.oa.platform.bootstrap.ImmutableTriggerInitializer` 在启动时执行：

1. 读 `classpath:db/trigger/immutable-triggers.sql`（4 个触发器 `sys_log` / `flow_signature` 的 UPDATE、DELETE）；
2. 按 `//` 切分，过滤出含 `CREATE TRIGGER` 的块（注释块自动跳过，支持 `-- [待定]` 形式保留未启用语句）；
3. 逐个查 `information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME = ?`；
4. **只创建缺失项**（幂等，重复启动安全）。

生产如需由 DBA 统一维护 DDL：`oa.db.immutable-triggers-enabled=false`（会打 WARN 提示失去数据库层兜底）。

---

## 5. 四层结构与包约定（doc/tech-design.md §4.1）

```
com.oa
├── common/       统一响应、错误码、审计、数据域织入、会话、Web 装配
│   ├── api/        ApiResponse / PageResult
│   ├── error/      ErrorCode（含 401/403/409 语义）/ BizException / GlobalExceptionHandler
│   ├── audit/      @Audited / AuditAspect / AuditLogWriter（只 INSERT）
│   ├── scope/      DataScopeType|Context|SqlBuilder|Interceptor|Table(Registry)
│   ├── security/   CurrentUser / AuthInterceptor / SessionStore / PasswordService / LoginAttemptGuard
│   ├── web/        WebMvcConfig / TraceIdFilter / TraceIds
│   └── config/     OaProperties / MybatisConfig
├── identity/      api→app→domain→infra：SysUser、会话、登录日志、AuthService
├── authz/         DataScopeResolver（sys_user+sys_role+sys_user_role 装载数据域）
├── form/          FormWritePolicy（三态白名单）
└── platform/      ImmutableTriggerInitializer
```

约定：`domain` 不依赖 Web；跨领域只经对方 `app`；**数据域过滤片段只允许出现在 `resources/mapper/**` 与 `common` 的织入器中**。

---

## 6. 数据域过滤：唯一入口（§5.3，安全核心）

**三件套**：鉴权阶段装载上下文 → Mapper 层 SQL 织入 → 字段级限制。

### 6.1 上下文装载

`AuthInterceptor`（校验会话 Cookie）→ `DataScopeResolver` 装载 `DataScopeContext`（ThreadLocal）：
用户、角色、数据域集合、归口类别、org 路径前缀、companyId、归口部门 id/路径。请求结束**必须** `clear()`。

### 6.2 SQL 织入（唯一入口）

- Mapper XML 在 WHERE 处写标记：`/* @dataScope(table=flow_instance, alias=i) */`；
- `DataScopeInterceptor`（MyBatis `Interceptor`，拦截 `Executor#query`）把标记替换为
  `DataScopeSqlBuilder` 的**纯函数**产物，`#{scope_*}` 参数经 MyBatis `additionalParameter` 绑定（无字符串拼接）；
- 受控表清单 = `oa.scope.tables` 配置 ∪ 实体上的 `@DataScopeTable` 注解（`SysUser` 已标注）；默认包含
  `flow_instance` / `form_data` / `flow_task` / `flow_routing` / `sys_user`；
- **受控表上的 SELECT 缺少标记即抛 40303 `DATA_SCOPE_MISSING`**（评审阻断项：禁止手写绕过过滤的裸查询）；
  免拦截清单见 `oa.scope.exempt-statement-ids`（登录/会话/字典等天然自限查询）；
- 标记必须占据一个**完整布尔表达式**的位置（`WHERE 1 = 1 AND <标记>`）：若拦截器未生效，SQL 会因悬空 `AND` 直接报错（fail-closed），而不是静默返回全表；
- 未装载上下文（登录前）或显式系统上下文（后台任务 `DataScopeContext.system()`）不受裸查询拦截约束，但会打日志留痕。

> **插件顺序（改代码前必读）**：MyBatis 的 `pluginAll` 是「后注册者在外层」。`DataScopeInterceptor` 标了
> `@Order(LOWEST_PRECEDENCE)`、`MybatisPlusInterceptor` 标了 `@Order(HIGHEST_PRECEDENCE)`，确保**先替换标记、再交给分页拦截器解析 SQL**；
> 若顺序反转，分页拦截器会用 JSqlParser 解析到悬空 `AND` 而失败。新增任何 Executor 级插件都必须保持本拦截器在最外层。

### 6.3 五路 SQL 片段（`DataScopeSqlBuilder` 常量原文，别名 `i`）

```
① self            (%1$s.initiator_id = #{scope_uid} OR EXISTS (SELECT 1 FROM flow_task t WHERE t.instance_id = %1$s.id AND (t.assignee_id = #{scope_uid} OR t.origin_assignee_id = #{scope_uid})) OR EXISTS (SELECT 1 FROM flow_cc c WHERE c.instance_id = %1$s.id AND c.user_id = #{scope_uid}))
② dept            (%1$s.initiator_org_path LIKE #{scope_dept_path_prefix})
③ company         %1$s.initiator_company_id = #{scope_company_id}
④ 归口类别         %1$s.form_type IN ('fund','contract','seal')
⑤ 涉费用事项单     (%1$s.form_type = 'matter' AND EXISTS (SELECT 1 FROM form_data f WHERE f.id = %1$s.form_data_id AND JSON_UNQUOTE(JSON_EXTRACT(f.fields_json, '$.involve_cost')) IN ('true','1')))
⑥ 流转链           EXISTS (SELECT 1 FROM flow_routing r WHERE r.instance_id = %1$s.id AND r.to_dept_id = #{scope_finance_dept_id})
⑦ 在途承接         %1$s.current_dept_id = #{scope_finance_dept_id}
⑧ 归口部门（默认关闭）%1$s.owner_dept_id = #{scope_finance_dept_id}
```

- 财务部 = ① self ∪ ④ 归口类别 ∪ ⑤ 涉费用事项单 ∪ ⑥ 流转链 ∪ ⑦ 在途承接（**五路并集**）；**「不涉及费用」且未经流转的事项单不可见**；
- 多角色 = 各口径并集后再**整体加括号**（`(…)`），保证以 `AND <片段>` 织入时不击穿前置条件；
- ⑧ 默认关闭：`flow_instance.owner_dept_id` 在 data-model 中定义为「恒为集团财务部」，无条件织入等于放开全部单据，与 PRD §5.3 冲突（见第 9 节）。

### 6.4 字段级限制（一期硬编码）

金额对非财务角色只读、导出仅系统管理员与财务角色（导出留痕）；手机号默认脱敏 `138****8888`
（`SysUser#maskedPhone()`）；金额一律字符串序列化（§7）。

---

## 7. 序列化与金额（禁止浮点）

`WebMvcConfig#oaJacksonCustomizer`：

- `BigDecimal` → **字符串**（金额 `DECIMAL(18,2)`，上限 `99,999,999,999.99`，服务端只接受字符串/定点数）；
- `Long` → 字符串（`BIGINT UNSIGNED` 主键超过 JS 53 位精度）；可用 `oa.jackson.serialize-long-as-string=false` 关闭；
- 时间 `yyyy-MM-dd HH:mm:ss`（库内存 UTC，展示 Asia/Shanghai）；未知字段不报错。

---

## 8. 审计与会话

- `@Audited(action, targetType, targetId, recordBefore, recordAfter, recordArgs)` + `AuditAspect` →
  `AuditLogWriter` → `AuditLogMapper`（**只有 INSERT 方法，刻意不继承 BaseMapper**）→ 数据库触发器再兜底拒绝 UPDATE/DELETE；
- 敏感键（口令、手机号、收款账号、令牌）在切面内统一脱敏为 `***`；
- 登录成功/失败由 `AuthService` 显式写 `sys_log` + `sys_login_log`（此时还没有登录人上下文）；登录、导出、权限变更、模板发布等敏感动作必须留痕；
- 会话：Cookie `OA_SESSION`（`HttpOnly + Secure + SameSite=Lax`），令牌 32 字节随机、库内只存 SHA-256（`token_hash`），
  Redis 键 `auth:session:{sha256(token)}`（与 normify 契约的 `auth:session:{token}` 语义对齐且不落明文）；
  「记住我」7 天、未勾选为会话级；同时在线设备上限 3，超出对 `login_at` 最早的会话**软踢出**（写 `revoked_at`，不物理删除）；
- 登录失败：Redis 键 `auth:fail:{account}`，5 次锁 15 分钟（`LoginAttemptGuard` 内 `InMemory` 实现供单测/降级）。

---

## 9. 已知差异与待确认项（**只记录，未改任何文档**）

| # | 位置 | 现象 | 骨架处理 |
| --- | --- | --- | --- |
| 1 | `sys_user` DDL | 没有 `must_change_password` 列，PRD/需求要求「首登强制改密」，无落点 | 暂以 `last_login_at IS NULL` 判定首登（`SysUser#mustChangePasswordOnFirstLogin`）；需 data-model 增列或独立标记表 |
| 2 | `flow_instance.owner_dept_id` | data-model 注释为「归口部门，**恒为集团财务部**」，而需求又要求用 `owner_dept_id = :financeDeptId` 作财务部可见性口径 → 会放开全部单据 | 该分支默认关闭（`oa.scope.finance-owner-dept-branch-enabled`），保留可配置开关；已按 PRD §5.3 用 `form_type IN ('fund','contract','seal')` 表达「归口类别」 |
| 3 | `sys_thread` 不可篡改 | tech-design §5.5 写「触发器拒绝 `sys_log`/`flow_signature`/`sys_thread` 的 UPDATE/DELETE」，data-model §8.1 与 `01-schema.sql` 只建了 4 个触发器（`sys_thread` 仅应用层约束） | 迁移脚本（主控维护）只含 4 个触发器；骨架对 `sys_thread` 未加触发器，按 data-model 口径执行 |
| 4 | `sys_log.target_type` | DDL 注释值域为 `instance/task/user/role/permission/template/org/dict`，但登出/踢出设备需要记录会话 | 骨架使用 `session`（DDL 注释未列），建议把值域补全 |
| 5 | `sys_log.action` | DDL 注释是示例串（`login/logout/create/…`），没有像 `sys_thread.action` 那样的 16 值定稿与 CHECK | 骨架只使用注释中出现过的动作码，建议补定稿 |
| 6 | `sys_role.data_scope = group_category` | 该口径同时被「财务部（归口）」与「集团分管领导（按业务线类别）」使用，但两者可见范围规则不同（前者还有流转链与在途承接） | 骨架按「财务部口径」实现（`DataScopeContext.categories` 已装载分管类别，SQL 细分留待阶段 2 与越权用例一起落） |
| 7 | `oa.session.store=memory` | 配置项存在，但 `SessionStore.InMemory` 仅为单测/降级预留，未装配为 Bean | 生产只支持 `redis`；如需降级需自行加装配（tech-design §2 的降级路径） |
| 8 | 分页上限 | 文档未给单页最大条数 | 骨架取 500（`MybatisConfig.MAX_PAGE_SIZE`），可在阶段 2 与前端约定后固化 |

---

## 10. 接口清单（阶段 1 已落地）

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | 登录并签发会话 Cookie | 免 |
| POST | `/api/v1/auth/logout` | 登出（软撤销，留痕） | 需 |
| GET | `/api/v1/auth/me` | 当前登录人 + 角色 + 数据域 | 需 |
| PUT | `/api/v1/auth/password` | 修改口令（首登强制改密同接口，成功后全部会话失效） | 需 |
| GET | `/api/v1/auth/password-policy` | 口令策略（前端前置提示） | 免 |
| GET | `/api/v1/auth/lock-status` | 账号锁定状态与剩余时长 | 免 |
| POST | `/api/v1/auth/unlock` | 管理员解锁（留痕） | 需（admin） |
| GET | `/api/v1/auth/sessions` | 在线设备列表 | 需 |
| DELETE | `/api/v1/auth/sessions/{sessionId}` | 远程注销设备会话 | 需 |

未落地（契约已存在，阶段 2/3）：`PUT /api/v1/auth/sessions/limit`（设备上限为运行期配置项，走管理后台）、
`GET /api/v1/authz/my-scope`、`POST /api/v1/authz/scope-preview`、`GET /api/v1/authz/scope-check`。

## 11. 阶段 1 边界（未实现，避免误读）

组织/人员导入、流程引擎与状态机、任务决议、表单模板引擎与四类单据、打印渲染、签名采集、通知与催办、
归档作业、报表、幂等与限流、文件上传下载鉴权、字典管理接口 —— 均**未实现**，本工程只提供骨架与三条安全基座
（统一响应 / 数据域唯一入口 / 审计只追加）。
