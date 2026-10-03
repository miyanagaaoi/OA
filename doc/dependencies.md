# 依赖清单（环境依赖 + DSH 插件依赖）

> 采集方式：**全部为实测值**，不是抄文档——后端版本用 `mvn dependency:list` 解析生效版本，前端读 `package.json`，DSH 侧读 `~/.dsh/profiles/web/package.json`、浏览器扩展 `manifest.json`、插件包 `package.json` 与 `resources/app/package.json`。
> 采集时间：2026-10-02。真源仍是各配置文件本身（`oa-server/pom.xml`、`oa-web/package.json`、`oa-deploy/docker-compose.yml`）；本文档是**汇总视图**，冲突时以配置文件为准。

---

## 1. 环境依赖

### 1.1 操作系统与基础工具

| 组件 | 本机开发实测版本 | 生产方式 | 用途 / 说明 |
| --- | --- | --- | --- |
| Windows | Windows 10/11（开发机） | Linux（生产） | 开发与生产异构；脚本为 PowerShell，生产走容器 |
| PowerShell | **5.1**（系统内置） | — | 启停脚本。**注意**：5.1 对无 BOM 的 `.ps1` 按 ANSI/GBK 解码，脚本必须带 UTF-8 BOM |
| Git | **2.55.0.windows.5** | 任意 | 版本控制；远程为 GitHub（本机需走代理） |
| Docker + Compose | 本机**未安装**（开发用便携版代替） | **必需**（24+） | 生产编排见 `oa-deploy/docker-compose.yml` |
| Nginx | 本机未装 | **1.24-alpine**（容器） | HTTPS/HSTS/CSP、静态资源、反向代理 |

### 1.2 语言与构建工具链

| 组件 | 实测版本 | 最低要求 | 出处 |
| --- | --- | --- | --- |
| JDK | **Temurin 21.0.12** | **21**（`pom.xml` 的 `java.version` 与 `maven.compiler.release`） | `oa-server/pom.xml` |
| Maven | **3.9.16** | 3.9+ | 后端构建 |
| Node.js | **24.19.0** | 前端 `engines.node >= 20.19.0`；文档工具脚本无下限但建议 ≥20 | `oa-web/package.json` |
| pnpm | **11.8.0** | 与 `packageManager` 锁定一致 | `oa-web/package.json` |
| Python | 未使用 | — | 全项目无 Python 依赖 |

### 1.3 数据与中间件

| 组件 | 本机开发（便携版） | 生产（容器） | 备注 |
| --- | --- | --- | --- |
| MySQL | **8.0.40**（`cdn.mysql.com/archives/...` 便携 zip） | **`mysql:8.0`** | 参数对齐：`utf8mb4` / `utf8mb4_general_ci` / `+08:00` / `log-bin-trust-function-creators=1`（建触发器必需）/ `max_connections=300` |
| Redis | **5.0.14.1**（tporadowski Windows 版） | **`redis:7-alpine`** | 仅用基础命令（SET/GET/EXPIRE/HSET），5.0 与 7 行为一致；**生产用 7** |
| 邮件 | 未接入 | 内网 SMTP 中继 | 一期唯一主动提醒通道（PRD 6.7） |

> 未采用 winget 提供的 MySQL **8.4.9**：本项目 DDL 与触发器在 **8.0** 上验证通过，8.4 未验证，不做无根据的版本跃迁。

### 1.4 网络依赖（仅开发期）

| 项 | 值 | 说明 |
| --- | --- | --- |
| HTTP/HTTPS 代理 | `http://127.0.0.1:7899` | Maven（`~/.m2/settings.xml`）、npm/pnpm、GitHub 拉取、MySQL zip 下载 |
| GitHub over SSH | `ssh.github.com:443` + `connect.exe -H 127.0.0.1:7899` | 直连 22 端口不通 |
| **生产** | **完全无公网** | 依赖必须离线化（见 §5） |

---

## 2. 后端依赖（`oa-server/pom.xml`）

版本由 **Spring Boot BOM 3.2.12** 统一管理（下表“实测版本”为 `mvn dependency:list` 解析出的生效值）。

| 构件 | 实测版本 | 作用 |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-parent` | **3.2.12** | 版本 BOM + 插件管理 |
| `spring-boot-starter-web` | 3.2.12 | REST / 内嵌 Tomcat |
| `spring-boot-starter-validation` | 3.2.12 | Bean Validation（`@NotNull` / `@Size` 等） |
| `spring-boot-starter-data-redis` | 3.2.12 | 会话、限流、幂等、调度锁、权限缓存 |
| `spring-boot-starter-aop` | 3.2.12 | `@Audited` 审计切面、数据域切面 |
| `spring-boot-starter-actuator` | 3.2.12 | `/actuator/health`（容器健康检查） |
| `com.baomidou:mybatis-plus-spring-boot3-starter` | **3.5.7** | ORM。**必须是 `-spring-boot3-` 构件**（`mybatis-plus-boot-starter` 只适配 Boot 2） |
| `com.mysql:mysql-connector-j` | **8.3.0** | JDBC 驱动（runtime）。连接串需 `characterEncoding=UTF-8` + `connectionCollation=utf8mb4_general_ci`（**不能写 `utf8mb4`**） |
| `org.flywaydb:flyway-core` | **9.22.3** | 版本化迁移 V1~V4 |
| `org.flywaydb:flyway-mysql` | 9.22.3 | MySQL 方言支持 |
| `org.springframework.security:spring-security-crypto` | **6.2.8** | **仅 BCrypt**，不引入完整 `spring-boot-starter-security`（否则默认拦截全部请求） |
| `spring-boot-starter-test` | 3.2.12 | JUnit 5 + Mockito + AssertJ（**当前基线 313 个测试全绿**） |
| `spring-boot-maven-plugin` | 3.2.12 | `spring-boot:run` / 可执行 jar |
| `maven-surefire-plugin` | 3.1.2 | 测试执行 |

**明确不使用**：JPA/Hibernate（`data-model.md` 的 SQL 可控性优先）、完整 Spring Security（见上）、Lombok（未引入，实体手写 getter/setter 以保证可读与可控）。

### 2.1 阶段 1.6 / 1.7 / 1.8 引入的第三方依赖：**无**

| 阶段 | 内容 | 新增第三方依赖 |
| --- | --- | --- |
| 1.6 | 字段级限制（金额只读 / 金额导出剔除 / 手机号脱敏展示） | **无**：纯 JDK（`java.util.regex` / `String`）+ 既有 Spring Web |
| 1.7 | 手机号 AES-256-GCM 字段级加密与密钥轮换 | **无**：只用 JDK 内置 `javax.crypto`（`AES/GCM/NoPadding`、`SecureRandom`、`Base64`）+ 既有 `spring-security-crypto`（BCrypt，§2 已列） |
| 1.8 | 五类批量导入（组织 / 人员 / 负责人 / 岗位 / 角色分配）与主数据导出 | **无**：CSV 解析与序列化由本仓库自研（`com.oa.admin.bulk.CsvTable` + `com.oa.identity.app.CsvSupport`，RFC4180 转义 + UTF-8 BOM） |

**为什么不需要 Apache POI**：`import-spec.md` **T-13 已定稿「CSV（UTF-8 BOM）为权威格式」**，五个模板（`org.csv` / `user.csv` / `org_leader.csv` / `user_position.csv` / `user_role.csv`）与导出物都是 CSV，可一键另存 `.xlsx` 供业务填报；服务端因此**只解析 CSV**，既不引入 `org.apache.poi:*`（POI 会带来数十个传递依赖与 CVE 面），也不把 `.xlsx` 的解析差异（日期/公式/合并单元格）带进校验口径。

**将来若确需在服务端直读 `.xlsx`**（业务坚持上传 Excel 而非 CSV）：再评估 `org.apache.poi:poi-ooxml:5.2.5`（与 Spring Boot 3.2 兼容线），届时按 §7「版本锁定与升级策略」登记版本、限制在**导入入口的格式适配层**内使用（解析后统一转成同一套 CSV 行模型，校验/落库逻辑不变），并同步更新本节与 `import-spec.md` T-13。

---

## 3. 前端依赖（`oa-web/package.json`）

| 构件 | 声明版本 | 用途 |
| --- | --- | --- |
| `vue` | `^3.4.38` | 框架 |
| `vue-router` | `^4.4.5` | 路由与登录守卫 |
| `pinia` | `^2.2.6` | 状态（用户/组织/权限缓存） |
| `element-plus` | `^2.8.8` | 组件库；主题通过 `tokens.scss` 令牌覆写 |
| `axios` | `^1.7.7` | HTTP 封装（统一响应、401 跳登录、traceId） |
| `typescript` | `~5.6.3` | 类型 |
| `vite` | `^5.4.11` | 构建（开发端口 **5273**，`/api` 代理到 8080） |
| `@vitejs/plugin-vue` | `^5.2.0` | SFC 支持 |
| `vue-tsc` | `^2.1.10` | 类型检查（两个 tsconfig） |
| `sass` | `^1.83.0` | SCSS 令牌与主题 |
| `@types/node` | `^22.9.0` | `vite.config.ts` 类型 |

**pnpm 构建脚本白名单**（`pnpm-workspace.yaml` 的 `allowBuilds`）：`esbuild`、`vue-demi`、`@parcel/watcher`。
> 放行 `esbuild` 是**必需**的：pnpm 10+ 默认不执行依赖的安装脚本，不放行会导致 `vite build` 直接失败。内网离线交付同样依赖这份白名单。

---

## 4. 交付与部署依赖（生产）

| 制品 | 版本/来源 | 说明 |
| --- | --- | --- |
| `mysql:8.0` | Docker Hub（离线需 `docker save`） | 数据 |
| `redis:7-alpine` | 同上 | 缓存/会话 |
| `nginx:1.24-alpine` | 同上 | 入口 |
| `oa-server:<tag>` | 由 `oa-server/Dockerfile` 多阶段构建 | 运行阶段基于 `eclipse-temurin:21-jre-jammy`（构建阶段 `maven:3.9-eclipse-temurin-21`） |
| `oa-web/dist` | `pnpm build` 产物 | 纯静态，由 Nginx 托管（不引用任何 CDN/在线字体） |

**离线化做法**（`oa-deploy/env-checklist.md` §2）：联网机 `mvn dependency:go-offline` 打包 `~/.m2/repository`；`pnpm install` 后打包 store/node_modules；`docker compose build` 后 `docker save` 镜像；证书随包提供。

---

## 5. 文档与结构工具依赖（`tools/*.js`）

**零外部依赖**：11 个脚本只用 Node 内置模块（`fs` / `path` / `https`）。不需要 `package.json`、不需要 `npm install`。

| 脚本 | 作用 |
| --- | --- |
| `gen-init-sql.js` | `data-model.md` / `dict-seed.md` → `01-schema.sql` / `02-dict-seed.sql`（含 7 类自检 + 重复加列漂移断言） |
| `build-flyway-migrations.js` | `oa-deploy/sql/*` → Flyway `V1~V4`，并把 `DELIMITER` 触发器段拆到 `db/trigger/`（含角色段顺序、9 码齐备、禁止点号权限码等断言） |
| `check-ddl.js` | 28 张表结构（主键/外键目标/重复列/金额禁浮点） |
| `check-templates-sql.js` | 模板 SQL（4 模板 × 7 节点、`form_schema_json` 合法性） |
| `check-import-csv.js` | 导入模板（表头/BOM/枚举/跨文件引用/工号唯一/角色码白名单） |
| `gen-permission-seed.js` / `check-permission-seed.js` | 权限树种子（9 角色 / 94 权限项 / 374 授权行）与 15 类断言 |
| `validate-design-md.js` | `DESIGN.md` 设计规范校验 |
| 其余（`fetch-github-repo.js`、`serve.js`、`png-probe.js`、`png-alpha.js`、`npx.js`、`pack-tgz.js`） | 辅助（拉取参考仓库、本地静态服务、PNG 检查、打包） |

CI 侧（`.github/workflows/docs-ci.yml`）用 `actions/setup-node@v4`（Node 20）、`actions/setup-java@v4`（Temurin 21 + Maven 缓存）、`pnpm/action-setup@v4`（pnpm 11）。

---

## 6. DSH 插件依赖

> 本项目的**结构基线**（`normify-oa/`，520 模块 / 865 API / 142 渲染层）与大量校验、子代理协作都依赖 DSH 及其插件。以下为实测版本。

### 6.1 DSH 运行时

| 包 | 实测版本 | 说明 |
| --- | --- | --- |
| `dsh-plugin-desktop-beta`（DSH Desktop 应用包） | **2.0.17-beta.1** | 桌面宿主 |
| `@deepseek-ai/dsh` | **0.2.0-rc.2** | 内核 |
| `@deepseek-ai/cordis` | **4.0.4** | 插件/依赖编排内核 |
| 其余 `@deepseek-ai/dsh-*` 内置包 | 0.2.0-rc.2 | 统一版本线 |

### 6.2 本会话实际用到的能力包（均随 DSH 内置）

| 能力 | 包 | 版本 |
| --- | --- | --- |
| **MCP 客户端**（承载 Normify 工具） | `@deepseek-ai/dsh-mcp-client`（+ `dsh-mcp-resources`） | 0.2.0-rc.2 |
| **技能框架** | `dsh-skill`、`dsh-tool-skill`、`dsh-skill-filesystem`、`dsh-skill-office`、`dsh-skill-badge`、`dsh-client-ui-skill` | 0.2.0-rc.2 |
| **AgentTeams（多代理协作，实验特性）** | `dsh-experimental-agent-team`、`-profile`、`-tool-agent-team`、`-client-ui-agent-team` | 0.2.0-rc.2 |
| **Web 检索/抓取** | `dsh-tool-web`、`dsh-web-search-deepseek`、`dsh-web-fetch-http`、`dsh-web-frontend` | 0.2.0-rc.2 |
| Web GUI 宿主 | `dsh-web`、`dsh-web-app`、`dsh-host-webserver` | 0.2.0-rc.2 |
| 会话/工作区/终端控制器 | `dsh-api-session-controller`、`-workspace-controller`、`-terminal-controller`、`-job-controller` | 0.2.0-rc.2 |

### 6.3 外部插件：Normify（本项目结构基线的生产者）

| 项 | 值 |
| --- | --- |
| 包名 / 版本 | **`@dsh-external/dsh-normify` 0.5.4**（ESM） |
| 分发形态 | tgz：`.cache/dsh-normify-0.5.4.tgz`（pnpm store 内亦有 `file+.plugins+dsh-normify-0.5.4.tgz`）；已解包副本 `.cache/normify-src` |
| 运行时依赖 | `yaml ^2.8.1` |
| 声明的 engines | `node >=18`；**`dsh >=0.1.5-rc.2 <0.2.0`** |
| 声明的 peerDependencies | `@deepseek-ai/cordis >=4.0.0-rc <5`（实测 4.0.4 ✅）；`@deepseek-ai/dsh-tools` 与 `@deepseek-ai/dsh-skill` 均 `>=0.1.5-rc.2 <0.2.0`（实测 0.2.0-rc.2 ⚠️ **高于声明上界**） |
| 提供的工具 | 22 个 `normify_*` 工具：`project_init` / `module_{upsert,batch,patch,get,list,move,delete,promote,refresh}` / `validate` / `build` / `render` / `layout_{get,upsert,delete}` / `deps_find` / `fingerprint` / `search` / `sync` / `outline` / `policy_{get,upsert}` / `change_{open,update,close,list}` / `tree_list` / `help` |
| 提供的技能 | `normify-gen`（分析仓库 → 生成/更新结构树） |
| 本项目产出 | `normify-oa/`：520 模块 / 865 API 契约 / 528 依赖箭头 / 142 渲染层；`tree.json`、`outline.md`、`api-index.json`、`receipt.json`、`normify.html` |

> ⚠️ **依赖声明与实测环境不一致（已知项）**：插件声明 `dsh <0.2.0`，而本机为 **0.2.0-rc.2**。实际使用正常（本项目全部结构操作、校验、构建、渲染均成功），但升级 DSH 或插件前应先确认该区间，避免"版本在声明外"带来的隐性风险。这是当前唯一一处**声明范围与实际不匹配**的依赖。

### 6.4 浏览器桥（页面验证用）

| 项 | 值 |
| --- | --- |
| 扩展 | `dsh-browser-extension` **0.1.4**（Manifest V3，`~/.dsh/browser-extension/`，install-info: managed + schemaVersion 1） |
| 桥接包 | `@yuxianglin/dsh-bridge-browser`（profile 内 `link:.dsh-browser-source`） |
| 其依赖 | `dompurify ^3.4.13`、`marked ^18.0.9`、`react ^18.3.1`、`react-dom ^18.3.1`、`tldts ^6.1.86` |
| 浏览器自动化 | **`playwright-core` 1.62.1**（随桥接包携带） |
| 用途 | 打开 <http://127.0.0.1:5273/> 做**真实页面验证**（登录、管理页、水印） |

### 6.5 DSH 配置与插件装载形态

| 项 | 值 |
| --- | --- |
| 用户配置目录 | `~/.dsh/`（`browser-extension/`、`dsh-browser/`、`profiles/`） |
| 生效 profile | **`dsh-profile-web`**（`~/.dsh/profiles/web/`） |
| 该 profile 的 bundles | `@deepseek-ai/dsh-base`、`@deepseek-ai/dsh-web-app`、`@yuxianglin/dsh-bridge-browser` |
| 补丁层 | `cordis.patch.yml` **为空数组 `[]`**（无自定义覆盖） |
| Web GUI | <http://127.0.0.1:43120>（本会话交互入口） |
| 插件包管理 | 通过 pnpm 安装（profile 内有 `pnpm-lock.yaml` / `pnpm-workspace.yaml`） |

### 6.6 本机为让插件可用而做的基础设施改动（与依赖相关）

| 项 | 说明 |
| --- | --- |
| PATH 内 git 转发器 | DSH 进程 PATH 中无 git，而 Normify 需要 `git rev-parse` 取修订号。已用 .NET `csc` 编译 4 KB 转发器放到 PATH 目录（`…\pnpm\bin\git.exe` → 真实 `mingw64\bin\git.exe`）；**重启 DSH 后机器 PATH 已含 Git，可删除该文件** |
| `~/.m2/settings.xml` | 配了 7899 代理（仅开发期；生产离线仓库不需要） |
| `~/.ssh/config` | GitHub 走 `ssh.github.com:443` + `connect.exe` 代理隧道 |

---

## 7. 版本锁定与升级策略

1. **后端**：版本集中在 Spring Boot BOM；只有 `mybatis-plus.version` 是本项目自有属性。升级 Boot 主版本需同步核对：MyBatis-Plus 构件名（`-spring-boot3-`）、Flyway 大版本、`spring-security-crypto`。
2. **前端**：`pnpm-lock.yaml` 入库并锁定；CI 用 `--frozen-lockfile`。升级 Element Plus 需复跑 `validate-design-md.js`（主题令牌依赖其变量）。
3. **数据库**：DDL 只能通过 `doc/data-model.md` 改 → 重跑生成器 → Flyway `Vn`。**已发布环境必须新增迁移版本，不得修改已执行的 Vn**（本项目尚未发布，故 V1 仍可修订）。
4. **插件**：DSH 与插件版本区间不一致时（当前：dsh-normify 声明 `<0.2.0`，实际 `0.2.0-rc.2`），先在小仓库验证再升级；结构库随仓库回档（`normify-oa/` 全量入库）。
5. **离线一致性**：生产交付前必须断网跑通（AC-33），依赖清单以 `env-checklist.md` §2 的离线包为准。

---

## 8. 依赖相关的已知风险

| 风险 | 影响 | 现状/对策 |
| --- | --- | --- |
| Normify 声明的 dsh 上界（`<0.2.0`）低于实际（`0.2.0-rc.2`） | 升级时可能行为变化 | 实测可用；升级前先在副本仓库跑 `validate`+`build` |
| 开发用 Redis 5.0（Windows 版）与生产 7 | 命令差异 | 仅用基础命令；生产镜像固定 `redis:7-alpine` |
| MySQL 8.0 vs 8.4 | 未验证 8.4 | 生产固定 `mysql:8.0`；8.4 需单独验证后再升级 |
| 触发器依赖 MySQL 客户端语法之外的 WMI/脚本行为 | 部署脚本 | 启停脚本仅用于开发；生产用 `docker-compose.yml` |
| 本机开发依赖 HTTP 代理（7899） | 换机需重配 | 生产无公网，依赖离线包；代理仅开发期 |
