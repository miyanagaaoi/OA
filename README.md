# 集团OA审批系统（Group OA Approval System）

面向**集团—公司—部门—科室**四级组织的一体化线上审批平台：四类审批单（事项 / 资金 / 合同 / 印鉴证照）在**一条 7 节点线性主干**上流转，支持会签/或签/依次审批、集团层链式流转与回退、补充材料、电子签名、A4 打印归档、审计留痕与十年留存。

- **部署形态**：本地私有化，不依赖任何公网服务（字体、静态库一律本地化）
- **规模口径**：总用户 300+（含子公司）、峰值同时在线 80、并发审批 30 TPS、页面 P95 ≤2s
- **一期边界**：无移动端推送、不对接企业微信/钉钉、无金额与类别路由、无第三方 CA

> 本仓库为**内部设计与结构资料库**：需求、数据模型、字段字典、设计规范、验收用例、开发排期与架构结构基线（Normify）。

---

## 文档导航

| 文档 | 内容 | 状态 |
| --- | --- | --- |
| [`doc/prd-0.1.md`](doc/prd-0.1.md) | **产品需求文档 V0.4**：范围、组织与权限、7 节点主干、异常路径、非功能、**61 条验收标准（AC-01~AC-61）**、里程碑、未决问题 | 评审稿 |
| [`doc/prd-v0.3-review.md`](doc/prd-v0.3-review.md) | **终审报告**：72 项一致性问题的证据、分级与逐条处置结果 | 已完成 |
| [`doc/data-model.md`](doc/data-model.md) | **数据模型与 DDL**：27 张表、索引、快照结构、数据域伪 SQL、不可变约束、归档策略 | V0.4 |
| [`doc/forms.md`](doc/forms.md) | **表单字段字典**：四类单据逐字段定义、三态读写模型、通用校验、附件限制、打印标签 | V0.4 |
| [`doc/enums.md`](doc/enums.md) | **枚举权威源**：节点码、状态机、决议模式、消息类型、轨迹动作、类别五值 | V0.4 |
| [`doc/dict-seed.md`](doc/dict-seed.md) | **数据字典种子**：8 个 dict_type / 37 行，含幂等 `INSERT` | V0.4 |
| [`doc/templates.md`](doc/templates.md) | **模板契约**：四类单据 × 7 节点配置、`form_schema_json` 结构、打印版式映射 | V0.4 |
| [`doc/test-cases.md`](doc/test-cases.md) | **可执行验收用例**：217 条，AC-01~AC-61 全覆盖，含越权/会签/补件/闸门/打印/非功能专项 | V0.4 |
| [`doc/dev-plan-v0.3.md`](doc/dev-plan-v0.3.md) | **开发步骤与排期**：阶段 0 + 5 阶段 23 周，工作包、决策闸门、验收门、关键路径 | 基线 |
| [`doc/tech-design.md`](doc/tech-design.md) | **技术方案**：总体架构、选型、模块划分、关键设计、部署运维、决策记录 D1–D10 | V1.0 已评审 |
| [`doc/import-spec.md`](doc/import-spec.md) | **批量导入规格**：组织/人员/负责人/一人多岗/角色分配五个模板、校验规则与错误码、幂等与回滚、补偿控制 | V1.1 |
| [`doc/dependencies.md`](doc/dependencies.md) | **依赖清单**：环境依赖（JDK/Maven/Node/MySQL/Redis/Nginx）、后端与前端构件实测版本、工具脚本零依赖说明、**DSH 插件依赖**（DSH 2.0.17-beta.1 / dsh 0.2.0-rc.2 / Normify 0.5.4 / 浏览器桥 0.1.4） | 2026-10-02 实测 |
| [`DESIGN.md`](DESIGN.md) | **设计语言**：设计令牌、组件规范、A4 打印规格（Stitch DESIGN.md 格式） | V0.4 |
| [`DESIGN.preview.html`](DESIGN.preview.html) | 全页面预览：登录 / 审批中心 / 四类表单 / 打印预览 / H5 / 审计日志 | 可打开 |
| [`DESIGN.print-a4.html`](DESIGN.print-a4.html) | 四类单据 A4 实尺打印稿 | 可打开 |
| [`doc/参考文档/`](doc/参考文档) | 集团现行纸质实单扫描件（版式基准） | 参考 |

---

## 架构结构基线（Normify）

[`normify-oa/`](normify-oa) 是**人机共读的模块树**：每个模块 = 一个 Markdown（frontmatter 面向机器，正文面向人），点开即下钻到子层，叶子承载 API 契约。

| 指标 | 数值 |
| --- | --- |
| 模块 | **527**（容器 142 / 叶子 385），最大深度 6 段 |
| API 契约 | **879**（http 698 / file 98 / kafka 30 / mysql 26 / rpc 21 / redis 6） |
| 依赖箭头 | **539**（单树跨分支，无悬空、无环） |
| 渲染数据 | **142** 层（每层含阅读顺序与导语） |
| 生命周期 | 全部 `state: planned`（代码未落地），`revision` 指向 `0c43a9d` |

- 交互视图：打开 [`normify-oa/normify.html`](normify-oa/normify.html) —— 点击下钻、悬停看介绍、`?lang=en` 切英文、`#module=<id>` 深链直达
- 机器读：`normify-oa/tree.json` ｜ 人读大纲：`normify-oa/outline.md` ｜ API 索引：`normify-oa/api-index.json` ｜ 编译回执：`normify-oa/receipt.json`

一级领域（13）：身份与组织、权限与数据域、流程引擎、表单与单据、电子签名、消息通知、审计与留痕、归档与历史库、管理后台、前端门户与移动端、接口与集成、平台与部署、设计语言与打印版式。

---

## 一期主干（7 个审批节点）

```
发起者 → ①直属部门负责人 → ②财务部复核【＝集团归口，只审一次；事项单「不涉及费用」时跳过】
      → ③分公司分管领导 → ④子公司总经理 → ⑤集团分管领导 → ⑥集团董事长 → ⑦归档登记 → 结束
```

- 协同/会签是 **②节点的并行子任务组**，不占主链编号
- **事项类别（经营/经济/行政/人力/投资）是配置项，不参与路由**，集团归口恒为财务部
- 流转 + 回退合计 ≤5、同节点被回退 ≤2、回到本部门连续 ≤2、补件同节点 ≤1 且全单 ≤3

---

## 快速开始

```bash
# 1) 看需求与验收
open doc/prd-0.1.md            # 或任意 Markdown 阅读器
# 2) 看界面与打印稿
open DESIGN.preview.html
open DESIGN.print-a4.html
# 3) 看结构树（交互）
open normify-oa/normify.html
# 4) 校验设计规范（需要 Node.js）
node tools/validate-design-md.js DESIGN.md
```

**目录结构**

```
├── DESIGN.md / DESIGN.preview.html / DESIGN.print-a4.html   # 设计规范与预览稿
├── doc/                                                      # 需求、模型、字典、模板、用例、排期、技术方案
├── normify-oa/                                               # 架构结构基线（527 模块 + 渲染数据 + 产物）
├── oa-server/                                                # 后端工程（Spring Boot 3.2 / Java 21，已含 Flyway 迁移）
├── oa-web/                                                   # 前端工程（Vue 3 + TS + Vite + Element Plus）
├── oa-deploy/                                                # 交付物：初始化 SQL、导入模板、Compose/Nginx、环境清单
├── tools/                                                    # 校验与生成脚本（见下表）
└── .github/workflows/docs-ci.yml                             # 文档、交付产物与前后端构建的持续校验
```

**工程骨架状态（阶段 1 开工）**：后端 `mvn test` **30 个单测全绿**（数据域 SQL 纯函数 / 三态白名单 / 会话锁定 / Mapper XML）；前端 `vue-tsc` + `vite build` 通过；本地工具链 JDK 21 + Maven 3.9 + Node 24/pnpm 11。

| 组件 | 启动方式 |
| --- | --- |
| 后端 | `cd oa-server && mvn spring-boot:run`（Flyway 自动执行 `V1/V2/V3`；触发器由启动时幂等创建） |
| 前端 | `cd oa-web && pnpm install && pnpm dev`（`/api` 代理到 8080） |
| 依赖（MySQL 8 / Redis 7 / Nginx） | `cd oa-deploy && cp .env.example .env && docker compose up -d` |

**脚本一览**（全部零依赖 Node，已接入 CI）：

| 脚本 | 作用 |
| --- | --- |
| `tools/gen-init-sql.js` | 由 `data-model.md` / `dict-seed.md` **生成** `oa-deploy/sql/01-schema.sql`、`02-dict-seed.sql`（`--check` 只校验） |
| `tools/check-ddl.js` | DDL 结构校验：表数、主键、外键目标、重复列、金额禁用浮点 |
| `tools/check-templates-sql.js` | 模板 SQL 校验：4 模板 × 7 节点、`form_schema_json` 合法性、闸门与签名口径 |
| `tools/check-import-csv.js` | 导入模板校验：表头、BOM、枚举、跨文件引用、工号唯一、正职唯一、角色码白名单 |
| `tools/build-flyway-migrations.js` | 由 `oa-deploy/sql/` 生成 `oa-server` 的 Flyway 迁移（`V1/V2/V3`），并把 `DELIMITER` 触发器段拆到 `db/trigger/immutable-triggers.sql` |
| `tools/validate-design-md.js` | `DESIGN.md` 设计规范校验 |

**结构维护规则（重要）**：`normify-oa` 各模块的 `source` 指向 `doc/prd-0.1.md`、`doc/data-model.md`、`doc/forms.md`、`DESIGN.md`，且 `fingerprint` 已按这些文档实算。因此**修改这四份文档后，需要执行一次**：

```
normify_module_refresh({ all: true, repoRoot: "H:\\dsh\\OA" })   # 重算指纹与 revision
normify_validate({ dir: "H:\\dsh\\OA\\normify-oa", repoRoot: "H:\\dsh\\OA" })   # 应为 0 error
```

再提交（否则 `normify_validate` 会报 `evidence/fingerprint-drift`）。

---

## 排期概览（23 周）

| 阶段 | 周次 | 内容 | 出口验收 |
| --- | --- | --- | --- |
| 0 | W0 | 技术方案、环境、模板、用例、**PRD/DDL 修订** | 技术方案评审；DDL 可执行；用例就绪 ✅ |
| 1 | W1–W4 | 组织权限与登录基座 | AC-01/02/17/18/33/37/41/57 |
| 2a | W5–W8 | 流程引擎内核 | AC-09/13/14/19/46/48~52 |
| 2b | W9–W12 | 表单模板、四类单据、异常路径、A4 打印 | AC-03~06/11/12/15/16/22~32/58 |
| 3 | W13–W16 | H5、电子签名、消息通知、附件 | AC-07/08/10/21/34/38/43~45/47/53/54 |
| 4 | W17–W20 | 管理后台、审计、归档、开放接口、非功能 | AC-20/35/36/39/40/42/55/56/59~61 |
| 5 | W21–W23 | 上线部署、数据初始化、培训、试运行 | 试运行退出条件 4 条 |

详见 [`doc/dev-plan-v0.3.md`](doc/dev-plan-v0.3.md)。

---

## 关键约束（开发必读）

1. **审批人快照**：发起时一次性解析并固化，此后调岗/离职/组织变更**不改动在途单据**（补偿控制：离职前必须清空待办、组织停用前必须清空在途）
2. **三态读写**：草稿全可写 / 审批中全只读 / 待补件仅附件与说明；**唯一例外**是印鉴证照单的「归还状态、归还日期」（发起人与节点⑦可改）；服务端白名单强制，不能只靠前端置灰
3. **不可篡改**：审计日志与签名记录只追加，数据库层拒绝 UPDATE/DELETE；保留 ≥10 年（登录日志 1 年）
4. **数据域**：子公司隔离；财务部可见「归口类别 + 涉及费用事项单 + 流转链承接」的单据，**不涉及费用且未流转的事项单不可见**；字段级限制（金额非财务角色只读不可导出、手机号脱敏）
5. **打印**：正式 A4 稿签名栏**一律空栏**；常规恰好一页、超长按行分页并标注页码；事项单统一使用子公司内部审批单版式
6. **无公网依赖**：构建与运行时依赖需离线化（Maven/npm 私服或离线仓库）

---

## 技术栈（已定稿）

| 层 | 选型 |
| --- | --- |
| 后端 | **Java 17/21 + Spring Boot 3.2 + MyBatis-Plus**（模块化单体，13 个领域分包） |
| 数据库 | **MySQL 8.0**（27 张表；不可篡改用触发器兜底） |
| 缓存/会话 | **Redis 7**（多设备会话与踢出、限流、幂等、调度锁） |
| 前端 | **Vue 3 + TypeScript + Vite + Element Plus**（主题对齐 `DESIGN.md` 令牌） |
| 部署 | **Nginx + Docker Compose**（单机私有化，离线镜像与离线依赖仓库交付） |
| 其他 | 文件：本地私有存储 + 鉴权下载 ｜ 打印：服务端 HTML + 浏览器 A4 ｜ 调度：应用内调度 + Redis 锁 ｜ 报表：库内聚合 |

详见 [`doc/tech-design.md`](doc/tech-design.md) V1.0（总体架构、关键设计、模块划分、决策记录 D1–D10、工程结构）。

---

*本仓库资料仅供集团内部项目实施使用。*
