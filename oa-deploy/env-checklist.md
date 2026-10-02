# 环境与交付清单（阶段 0 · 工作项 0.3）

> 依据：[`tech-design.md`](../doc/tech-design.md) §7 部署与运维、§6 非功能设计；[`prd-0.1.md`](../doc/prd-0.1.md) 第 9 章（REQ-NFR-001/003/005/008/010、AC-33/35/36/39/40/41/42）
> 交付形态：**单机私有化 + Docker Compose**（D4），Nginx 前置，MySQL 8 + Redis 7 + 本地文件卷。

---

## 1. 三套环境

| 环境 | 用途 | 规格（建议） | 数据 | 允许公网 |
| --- | --- | --- | --- | --- |
| **开发（dev）** | 日常编码与联调 | 1 台 4C8G；MySQL 8 / Redis 7 可共用 | 造数（种子 + 模板导入） | 仅构建机需要（拉依赖），运行时可断网 |
| **测试（test）** | 功能与越权测试、打印目视 | 1 台 4C8G，与开发隔离 | 造数 + 测试用例数据 | 否 |
| **预发/生产（staging/prod）** | 试运行与正式上线 | 2C8G 起（应用 + 库 + 缓存同机）；磁盘 ≥500G（附件 + 备份） | 真实组织与人员（Excel 导入） | **完全无公网** |

**容量口径（REQ-NFR-003）**：总人数 300+、峰值同时在线 80、峰值并发审批 30 TPS、按 3 年数据量（≥5 万单据）设计。

> 若客户不允许容器化，按技术方案 §3.2 备选改为 Linux + systemd 直装，应用与 Nginx 的配置项保持不变。

---

## 2. 离线依赖（REQ-NFR-001 / AC-33）

内网环境**必须**预先准备，运行时不得访问公网：

| 类别 | 做法 |
| --- | --- |
| 后端依赖 | 联网机执行 `mvn dependency:go-offline`，整体打包 `~/.m2/repository`（或搭 Nexus 私服） | 
| 前端依赖 | 联网机执行 `pnpm install` 后打包 `node_modules` + pnpm store，或在 `oa-web/` 内提交锁文件 `pnpm-lock.yaml` 并携带离线 tarball |
| 容器镜像 | `docker compose build` 后 `docker save oa-server:tag nginx:1.24-alpine mysql:8.0 redis:7-alpine \| gzip > oa-images.tgz` |
| 字体与静态资源 | **不使用任何 CDN/在线字体**；字体族走系统栈（`DESIGN.md` 令牌已声明），图标用 Element Plus 内置或本地 SVG |
| 证书 | 集团内网 CA 签发；自签时随包提供 `fullchain.pem` / `privkey.pem` |

**断网验收（AC-33）**：拔掉外网后完成「登录 → 发起 → 三级审批 → 打印」全链路，页面无外部请求失败（浏览器 Network 面板无第三方域名）。

---

## 3. 端口与账号

| 组件 | 端口 | 暴露范围 | 说明 |
| --- | --- | --- | --- |
| Nginx | 443 / 80 | 客户端可访问（80 仅跳转） | 全站 HTTPS |
| 应用 | 8080 | 仅容器网络内 | 只经 Nginx 对外 |
| MySQL | 3306 | **127.0.0.1** | 不对公网暴露 |
| Redis | 6379 | **127.0.0.1** | 设 `requirepass` |
| SMTP | 25/465/587 | 出口到内网邮件中继 | 一期唯一的主动提醒通道（PRD 6.7） |

**账号**：数据库应用账号只授予 DML + 必要 DDL（Flyway 迁移用）；**不允许**应用账号 `DROP`；Redis 独立口令；系统首个管理员由初始化脚本创建并在首次登录强制改密。

---

## 4. 运行期配置（后台可配，不发版）

决议模式与阈值、节点超时（②48h / 其余 24h）、闸门次数（流转+回退 ≤5、同节点回退 ≤2、回到本部门连续 ≤2、补件 ≤3）、强制签名节点（⑤⑥）、会话上限（默认 3 台、记住我 7 天）、锁定策略（失败 5 次锁 15 分钟）、最小超时（≥24h）、保留期（审计 ≥10 年 / 登录日志 1 年）、导出权限（主数据仅管理员；金额类管理员+财务）、自由跳转开关（默认全关）。

> 这些值**不进代码**，由管理后台维护并写审计日志（AC-20）。

---

## 5. 备份、恢复与归档

| 项 | 要求 |
| --- | --- |
| 备份内容 | **数据库 + 附件卷（`oa-files`）**，二者必须同一批次备份 |
| 频率 | 每日 01:00 全量，保留 30 天（`oa-deploy/backup/` 挂载卷） |
| 恢复演练 | **每季度一次**，恢复到隔离环境并核对：单号可检索、附件可下载、审计日志完整（AC-40） |
| 目标 | RPO ≤24h、RTO ≤4h |
| 归档 | 完结满 3 年（可配）转历史库并置只读，仍可按单号检索与预览（AC-42） |
| 保留 | 审计日志与审批轨迹 ≥10 年、登录日志 1 年（AC-39） |

---

## 6. 时间、日志与监控

- **时钟同步**：所有节点接入内网 NTP；时钟漂移会导致超时催办与轨迹时间错乱
- 时区统一 `Asia/Shanghai`
- 日志：应用日志（按天滚动，保留 ≥90 天）、慢查询日志、Nginx 访问日志；**审计日志在库内且只追加**
- 监控告警：应用健康（`/actuator/health`）、慢查询、定时任务失败（超时扫描/补件扫描/归档）、磁盘水位、连接池水位
- 可用性口径：工作日 8:00–20:00，目标 ≥99.5%

---

## 7. 安全基线（AC-41 逐项勾选）

| # | 项 | 落地方式 |
| --- | --- | --- |
| 1 | 全站 HTTPS + HSTS | Nginx 配置（`nginx/oa.conf`） |
| 2 | 口令策略 | ≥8 位含字母与数字；BCrypt 存储；失败 5 次锁 15 分钟 |
| 3 | 敏感字段加密 | 手机号 AES-256-GCM；密钥轮换 |
| 4 | 越权防护 | 服务端统一数据域过滤（唯一入口）+ 接口级权限 + 越权专项用例 |
| 5 | 字段级限制 | 金额对非财务角色只读、不可导出；手机号脱敏 |
| 6 | 会话与设备 | HttpOnly + Secure + SameSite Cookie；多设备上限与踢出 |
| 7 | 附件鉴权 | 私有目录 + 下载鉴权；扩展名与 MIME 双校验 |
| 8 | 审计留痕 | 登录、导出、权限变更、模板发布、改派等全部入 `sys_log` |
| 9 | 不可篡改 | `sys_log` / `flow_signature` 触发器拒绝改删 |
| 10 | 导出留痕 | 导出动作写审计；范围受数据域限制 |
| 11 | 备份 | 每日全量 + 季度恢复演练 |
| 12 | 错误信息 | 统一异常处理，不泄露堆栈与内部路径 |

---

## 8. 部署步骤（首次）

```bash
# 0) 前置：Docker + Compose、内网 NTP、证书、SMTP 账号
# 1) 解包交付物（镜像包 + 目录）
docker load -i oa-images.tgz
# 2) 配置
cp oa-deploy/.env.example oa-deploy/.env && vi oa-deploy/.env
# 3) 前端产物（如未随包提供）
cd oa-web && pnpm install --offline && pnpm build && cd ..
# 4) 启动
cd oa-deploy && docker compose up -d
# 5) 建库与种子（应用启动时 Flyway 自动执行 V1/V2/V3）
docker compose logs -f app | grep -i flyway
# 6) 组织与人员导入（OA 管理后台 → 批量导入，按 doc/import-spec.md 顺序）
#    组织 → 人员 → 负责人 → 一人多岗 → 角色分配
# 7) 冒烟：登录 → 发起四类单据各一单 → 走完 7 节点 → 打印目视 → 审计留痕
```

**升级发布**：`docker compose pull/build` → `docker compose up -d app`（滚动重启）；数据库变更随 Flyway 版本化迁移；**破坏性变更需评审**。

---

## 9. 上线前自检（SQL）

```sql
SELECT code, version, node_count, JSON_VALID(form_schema_json) FROM flow_template ORDER BY code;   -- 期望 4 行 / 各 7 节点
SELECT t.code, COUNT(*) FROM flow_node n JOIN flow_template t ON t.id = n.template_id GROUP BY t.code;  -- 期望各 7
SELECT dict_type, COUNT(*) FROM sys_dict_item GROUP BY dict_type ORDER BY dict_type;               -- 期望 8 类 / 37 行
SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE();                -- 期望 4（不可篡改）
UPDATE sys_log SET action='x' WHERE id=1;      -- 必须失败：sys_log is append-only
DELETE FROM flow_signature WHERE id=1;         -- 必须失败：flow_signature is append-only
```

---

## 10. 未闭项（需客户/业务提供）

| # | 事项 | 影响 |
| --- | --- | --- |
| E-1 | 服务器与磁盘规格、是否允许容器 | 决定部署形态（D4 主方案 / systemd 备选） |
| E-2 | SMTP 中继地址、发信人、是否有发信频率限制 | 一期唯一主动提醒通道 |
| E-3 | 内网 CA 是否可签发服务器证书 | 决定是否使用自签（浏览器信任需下发根证书） |
| E-4 | 备份介质与异地留存要求 | 影响 RPO/RTO 与介质成本 |
| E-5 | 是否已有人事/财务编码体系 | 决定是否新增 `sys_org.org_code`（当前以 `org_path` 为业务键） |
