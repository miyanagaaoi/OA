# 集团OA审批系统 · 枚举权威源（enums.md）

| 项目 | 内容 |
| --- | --- |
| 文档用途 | 定义全系统**全部代码型枚举**（状态机、节点码、解析规则码、动作、消息类型、字段类型、日志类别）的唯一权威取值，并划清「枚举」与「数据字典」的边界 |
| 权威边界 | 本文档是**全项目唯一**的枚举定义处。`doc/dict-seed.md`、`doc/templates.md`、`data-model.md` 的 CHECK 约束、`forms.md` 的字段取值说明，出现冲突时**一律以本文档为准** |
| 与 PRD 的关系 | PRD（`prd-0.1.md`，V0.4）正文只描述**行为语义**，不重复罗列 code；code 一律回引本文档 |
| 与 data-model.md 的关系 | `CHECK` 约束与列注释必须与本文档取值**逐字一致**；本文档不定义表结构，表结构以 `data-model.md` 为准 |
| 与 forms.md 的关系 | 字段**类型**部分（§11）与 `forms.md` 1.1 对齐；字段**取值来源**（枚举 or 字典）见 §1.2，字典**种子数据**见 `doc/dict-seed.md` |
| 与 templates.md 的关系 | `doc/templates.md` 的流程模板与 `form_schema_json` 必须引用本文档 code，**不得自定义新值** |
| 版本 | **V0.4 配套** · 状态：评审稿 · 基准日期：2026-07-09 |
| 维护责任 | 枚举值一经使用**不得复用、不得改义**；废弃值保留在表中并标注「已废弃 → 替代值」 |

---

## 0. 权威性与定稿结论

### 0.1 维护规则

1. **枚举改动 = 发版改动**。枚举参与程序分支与数据库 `CHECK` 约束，值域变更必须走代码发布 + 迁移脚本。
2. **只增不改**。新增值可平滑发布；删除或改义必须新增值并把旧值标为废弃（例：`operate` → `business`）。
3. **前后端同源**。前端不得硬编码中文字符串，必须按 code 渲染中文名。
4. **小写蛇形**。全部 code 使用 `^[a-z][a-z0-9_]{1,31}$`，不用大写、不用连字符、不用中文。
5. **本文档只定义「值域」**，不定义取值之间的**流转规则**（状态机迁移规则见 `prd-0.1.md` 7.2 与 `data-model.md`）。

### 0.2 业务裁定结论（原「待业务确认项」，**全部已关闭**）

> 本节所有条目已由业务方裁定，结论已落入本文档正文。**业务侧无未决项**；仅剩技术执行项，单列于 §0.2.1。

| 编号 | 原待确认事项 | 定稿结论（落地位置） | 状态 |
| --- | --- | --- | --- |
| E-01 | 事项类别「经营」的 code 命名 | 定稿 `business`（§10.1）；存量 `operate` 按 §14 迁移 | **已关闭** |
| E-02 | 归档登记节点（⑦）的审批人角色码与是否审批 | 角色码 `finance_clerk`（财务部内勤）；⑦**默认「仅登记不审批」**——不产生审批决议、不需要决议模式与阈值、不计入审批时长与效率统计，仅留痕（`sys_thread.action = archive_register`）；可按模板配置改为需审批（§2） | **已关闭** |
| E-03 | 协同/会签任务的消息类型粒度 | 保留 `collaboration`（§8）；**每个协同部门产生一条站内信**（N 个协同部门 = N 条 `collaboration` 站内信），同时每个部门各产生 `todo` 待办 | **已关闭** |
| E-04 | 节点实例「已退回」是否终态 | **非终态**（§5）：上一节点重审通过后本节点回到 `active` | **已关闭** |
| E-05 | 附件轮次 `round` 上限 | 沿用 `0..3`（§12.3），与「全单补件 ≤3 次」一致 | **已关闭** |
| E-06 | 日志保留期 | 按 **PRD REQ-NFR-007 执行**：审计日志与审批轨迹 ≥10 年、登录日志 1 年、签名记录永不删除（§13）。**已确认，无需法务复审** | **已关闭** |
| E-07 | 附件允许格式是否放行 `heic` / `wps` | **放行**：允许格式 13 → **15 种**（§12.1）；禁止格式清单不变；服务端须做**预览降级**（`heic` 转 `jpg`、`wps` 提示下载查看） | **已确认（放行 `heic` 与 `wps`）** |

### 0.2.1 技术执行项（非业务决策）

| 编号 | 技术项 | 待办 | 责任面 |
| --- | --- | --- | --- |
| A-01 | `flow_node.decision_mode` 的可空性 | ⑦为登记节点，`decision_mode` / `pass_threshold` 置 `NULL`；`data-model.md` 4.2 需允许 `node_type = 'archive'` 时 `decision_mode` 为 NULL（或由实现写 `'any'` 占位、引擎跳过决议计算） | 后端 + `data-model.md` |
| A-02 | `sys_thread.action` 取值 | ⑦留痕动作定稿为 `archive_register`；`data-model.md` 6.5 现写 `archive`，须同步（见 S-14） | `data-model.md` |
| A-03 | `heic` / `wps` 预览降级 | `heic` 服务端转 `jpg` 后预览、`wps` 提示下载查看；**不影响上传白名单** | 前端 + 文件服务 |
| A-04 | `sub_status` 存量值 | `data-model.md` 5.1 现写 `supplement`，须改为 `pending_supplement`（见 S-05） | `data-model.md` |

### 0.3 与既有文档的差异（回写清单，本文档不修改既有文档）

| 编号 | 既有文档位置 | 既有写法 | V0.4 定稿 | 处置 |
| --- | --- | --- | --- | --- |
| S-01 | `forms.md` 6.1 / `data-model.md` 5.1 | 事项类别经营 = `operate` | `business` | 由主控在 V0.4 同步；数据侧按 §14 迁移 |
| S-02 | `forms.md` 2「业务补充说明」 | 「category 是全局唯一路由判据」 | **类别是配置项、五值、不参与路由** | 表述作废，以 PRD 6.1 为准 |
| S-03 | `prd-0.1.md` 6.3 / `forms.md` 9.2 | 「主干为 8 个节点」 | 主干 **7 个审批节点**（①–⑦，编号即节点数） | 计数口径更正 |
| S-04 | `data-model.md` 4.2 | `node_code` = `department/finance/company_exec/gm/group_dept/group_exec/archive` | §2 的 7 个新码 | 建库前替换 |
| S-05 | `data-model.md` 5.1 | `sub_status` = `supplement` | `pending_supplement` | 建库前替换 |
| S-06 | `data-model.md` 5.2 | 节点实例状态 `processing / awaiting_supplement` | `active / waiting_supplement` | 建库前替换 |
| S-07 | `data-model.md` 5.3 | 任务状态 `returned / supplement / closed` | `rolled_back / supplement_requested / auto_closed` | 建库前替换 |
| S-08 | `data-model.md` 5.4 | 流转动作 `return_node` | `rollback` | 建库前替换 |
| S-09 | `data-model.md` 6.4 | `msg_type` 无协同类型 | 新增 `collaboration` | 建库前补充 |
| S-10 | `data-model.md` 6.5 | 轨迹动作 `addsign / return_node / archive` | `add_sign / rollback`；`archive` 归入补充值 | 建库前替换 |
| S-11 | `data-model.md` 3.6 | `dict_type` = `category / pay_method / cert_name` | `matter_category / payment_method / cert_type` | 见 `doc/dict-seed.md` §0 |
| S-12 | `prd-0.1.md` 5.3 / 9.1 | 「导出仅系统管理员」 | **系统管理员与财务角色**均可导出；非财务角色仅可见不可导出 | 权限口径更新 |
| S-13 | `forms.md` 5 | 归还状态「仅归档节点（⑦）可改」 | 审批中**发起人与节点⑦**均可改 | 白名单扩充 |
| S-14 | `data-model.md` 6.5 | 轨迹动作 `archive` | `archive_register`（⑦留痕动作，与节点码同名） | 建库前替换 |
| S-15 | `forms.md` 6.3 / 6.5 | 合同类型拆为「采购 / 销售」；证照类型 5 值；无 `cert_seal` | 定稿：合同类型**保留单一「购销」**（不拆）；`cert_seal`（证照章）与 `cert_borrow`（证照借用）**并存**；证照类型 5 值（**不含开户许可证**） | 由主控在 V0.4 回写 `forms.md`；字典侧以 `doc/dict-seed.md` §2/§3/§4 为准 |

> **同步状态（本轮核对）**：S-04、S-06、S-07、S-08、S-09、S-11 已在 `data-model.md` 同步；S-01 已在 `forms.md` 6.1 同步（`business`）。**仍为旧值、需回写**：S-05（`data-model.md` 5.1 `sub_status = 'supplement'`）、S-14（`data-model.md` 6.5 `archive`）、S-15（`forms.md` 6.3/6.5）。S-02、S-03、S-12、S-13 以主控 V0.4 正文为准。

---

## 1. 全局约定

### 1.1 命名与存储

| 项目 | 约定 |
| --- | --- |
| 命名 | 小写蛇形 `^[a-z][a-z0-9_]{1,31}$`；不用驼峰、不用连字符 |
| 数据库类型 | `VARCHAR(16)` / `VARCHAR(24)` / `VARCHAR(32)` + `CHECK` 约束（便于扩展，不改表结构） |
| JSON 内取值 | 与数据库取值**完全一致**，不做二次映射 |
| 缺省语义 | `NULL` 表示「不适用 / 未配置」，**不等于**任何枚举值；不得用空字符串代替 `NULL` |
| 前端 | 只传 code，中文名由前端字典表渲染 |

### 1.2 枚举 vs 数据字典的边界（**核心**）

出现「这个值到底放枚举还是放字典」的争议时，按下表判定：

| 载体 | 判定标准 | 存放位置 | 能否后台增删 | 本文档章节 |
| --- | --- | --- | --- | --- |
| **代码枚举** | 值参与**程序分支、状态机迁移、CHECK 约束** | 代码常量 + 本文档 | **否**（改动需发版） | §2–§9、§11、§13 |
| **数据字典** | 值只影响**展示与选项列表**，不改变程序行为 | `sys_dict_item` | **是**（新增无需发版） | 见 `doc/dict-seed.md` |
| **checkbox 布尔字段** | 字段本身是布尔/枚举，**选项固定且不参与分支** | `form_schema_json.options` | 否，但改选项不需发版（模板升版本即可） | 见 `doc/dict-seed.md` §6–§7 |

**判定口诀**：删掉这个值，程序会不会少走一条分支？会 → 枚举；不会，只是表单少一个选项 → 字典。

### 1.3 关键边界结论（避免两处定义）

| 值域 | 归属 | 理由 |
| --- | --- | --- |
| 流程节点码、审批人解析规则码 | **枚举** | 引擎按码取人 |
| 实例状态 / 子状态 / 节点实例状态 / 任务状态 | **枚举** | 状态机迁移 |
| 流转动作、审批轨迹动作、消息类型 | **枚举** | 驱动动作分发与通知路由 |
| 事项类别 `matter_category` | **字典** | 五值不参与路由（集团归口恒为财务部），仅作分类标签与统计维度 |
| 单据类型 `form_type` | **枚举** | 决定表单模板与流程模板装配 |
| 合同类型 / 用印类型 / 证照类型 / 付款方式 / 归还状态 / 其他会审部门 | **字典** | 纯展示型选项 |
| 计划类别 `plan_category` / 付款归属 `payment_belong` | **checkbox 布尔字段**（不是字典项） | 定稿为布尔字段（默认均勾选），值存 `fields_json`；**字段 code 统一为 `plan_category`**；见 `doc/dict-seed.md` §6–§7 |
| `sign_policy` / `decision_mode` | **枚举** | 引擎按值改变决议与签名行为 |
| 字段类型（14 种） | **枚举** | 渲染器按码选控件与校验器 |

---

## 2. 流程节点码（主干 7 个审批节点）

**范围**：`flow_node.node_code`、`flow_node_instance.node_code`、`flow_instance.approver_snapshot_json.nodes[].node_code`。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `dept_leader` | 直属部门负责人 | Direct Department Leader | 节点①。取发起人所在科室负责人；科室未设负责人时**上溯**取所属部门负责人 | `flow_node.node_code` |
| `finance_review` | 财务部复核 | Finance Review | 节点②。**= 集团归口部门，只审一次**；由「是否涉及费用」决定是否跳过（仅事项单会出现跳过）。跳过时节点实例状态为 `skipped`，但单据归口部门**仍记为财务部** | `flow_node.node_code` |
| `branch_leader` | 分公司分管领导 | Branch Company Executive | 节点③。按发起人所属公司匹配分公司绑定的分管领导 | `flow_node.node_code` |
| `subsidiary_gm` | 子公司总经理 | Subsidiary General Manager | 节点④。取发起人所属公司的总经理 | `flow_node.node_code` |
| `group_leader` | 集团分管领导 | Group Executive | 节点⑤。按事项类别匹配集团层绑定的分管领导；**默认强制签名** | `flow_node.node_code` |
| `chairman` | 集团董事长 | Group Chairman | 节点⑥。取集团董事长；**默认强制签名** | `flow_node.node_code` |
| `archive_register` | 归档登记 | Archive Registration | 节点⑦。集团处理部门流转、归档登记。**默认「仅登记不审批」（V0.4 定稿）**：不产生审批决议、不需要决议模式与阈值、**不计入审批时长与效率统计**，仅留痕（`sys_thread.action = archive_register`）；可按模板配置改为需审批 | `flow_node.node_code` |

**约束**：

- 主干节点**固定 7 个、顺序固定**；一期无条件路由，金额不参与路由。
- **协同 / 会签是②的并行子任务组，不占主链编号**，不产生独立的 `node_code`；其子任务在 `flow_node_instance` 中以 `node_seq = 2` + `dept_id` 区分（唯一键 `(instance_id, node_seq, dept_id)`）。
- 只有**事项审批单**的②可能为 `skipped`；资金、合同、印鉴三类单据恒为「涉及」。
- 节点⑦**默认「仅登记不审批」**：通过 `flow_node.sign_policy = 'none'` + `decision_mode = NULL`（登记节点无决议）+ 单人候选实现，不新增枚举值；该节点**不产生审批决议、不计入审批时长与效率统计**，仅写 `sys_thread` 留痕。管理员可在模板中改配为需审批（届时按审批节点配置 `decision_mode` 与 `sign_policy`）。

---

## 3. 审批人解析规则码

**范围**：`flow_node.approver_rule`、`flow_instance.approver_snapshot_json.nodes[].rule`。

> **命名空间独立**：节点码描述「流程位置」，解析规则码描述「取人算法」。命名空间独立，允许同名（如 `branch_leader` 既是节点码也是解析规则码），二者**不构成耦合**。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `dept_leader_upward` | 直属部门负责人上溯 | Department Leader (Upward) | 取发起人科室负责人；科室无负责人则上溯到部门负责人；仍为空则**禁止发起** | `flow_node.approver_rule` |
| `finance_owner` | 集团财务部负责人 | Group Finance Owner | **恒取集团财务部负责人**（五个类别统一归口，不细分）；该节点同时承担费用复核与集团归口审批 | `flow_node.approver_rule` |
| `branch_leader` | 分公司分管领导 | Branch Executive Resolver | 按发起人 `company_id` 匹配该公司绑定的分管领导 | `flow_node.approver_rule` |
| `subsidiary_gm` | 子公司总经理 | Subsidiary GM Resolver | 取发起人所属公司的总经理 | `flow_node.approver_rule` |
| `group_leader` | 集团分管领导 | Group Executive Resolver | 按事项类别匹配集团层业务线绑定的分管领导 | `flow_node.approver_rule` |
| `chairman` | 董事长 | Chairman Resolver | 取集团董事长（唯一） | `flow_node.approver_rule` |
| `designated` | 指定人员或角色 | Designated User or Role | 由 IT 部门在流程设计器中固定指定；参数存 `approver_param`，形如 `{"user_ids":[1001,1002]}` 或 `{"role_code":"finance_clerk"}` | `flow_node.approver_rule` + `approver_param` |
| `initiator_pick` | 发起人自选 | Initiator Pick | 发起时由发起人从通讯录中选择候选人 | `flow_node.approver_rule` |
| `collab_dept_leader` | 协同部门负责人 | Collaboration Dept Leader | **仅用于②的并行子任务组**；由②的审批人在审批时勾选协同部门，系统对每个被勾选部门取该部门负责人 | `flow_node.approver_rule`（并行子任务） |

**共同约束**（对全部规则生效）：

1. 解析**只在流程发起时执行一次**，结果固化进 `approver_snapshot_json`；在途单据不因调岗/离职/组织调整而变更。
2. 候选人集合为空 → **禁止发起**并提示「XX 节点无有效审批人，请联系管理员配置」，**不允许静默跳过**。
3. 同一人在同一节点重复出现时自动去重。
4. 同一人同时是多节点审批人时默认逐节点分别审批，不做连续节点合并。

---

## 4. 流程实例状态与子状态

**范围**：`flow_instance.status`、`flow_instance.sub_status`。

### 4.1 主状态 `status`

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `draft` | 草稿 | Draft | 未提交；全部字段可写 | `flow_instance.status` |
| `approving` | 审批中 | Approving | 已提交，主干流转中；**待补件时主状态仍为 `approving`** | `flow_instance.status` |
| `approved` | 已通过 | Approved | 全部节点通过（含⑦登记完成）；终态 | `flow_instance.status` |
| `rejected` | 已驳回 | Rejected | 任一节点驳回，回到发起人；**可回到草稿**修改后重提（重提会重新解析审批人快照与流程版本） | `flow_instance.status` |
| `withdrawn` | 已撤回 | Withdrawn | 发起人在**节点②通过前**撤回；**可回到草稿** | `flow_instance.status` |
| `terminated` | 已终止 | Terminated | 仅系统管理员与集团分管领导可终止；**终态，不可回到草稿、不可再提交** | `flow_instance.status` |

> **回到草稿规则（定稿）**：`rejected` 与 `withdrawn` 两个状态**均可回到 `draft`**；`approved` 与 `terminated` 为不可逆终态。实现口径与 `data-model.md` 8.2 一致：`withdrawn` 为**到达态**，写入 `sys_log` 与 `sys_thread` 后 `flow_instance.status` 即回到 `draft`，不在待办列表中长期驻留，历史查询以审计日志与审批轨迹为准。

### 4.2 子状态 `sub_status`

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `pending_supplement` | 待补件 | Pending Supplement | 审批人请求补充材料后置位；仅发起人可补**附件与备注**，主字段只读；补完回到请求补件的节点；**不算驳回**，不写驳回记录、不计入驳回率 | `flow_instance.sub_status` |

> `sub_status = NULL` 表示无子状态。子状态不改变主状态：待补件期间 `status` 仍为 `approving`。

---

## 5. 节点实例状态

**范围**：`flow_node_instance.status`。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `pending` | 未开始 | Pending | 尚未轮到本节点 | `flow_node_instance.status` |
| `active` | 进行中 | Active | 已产生任务，等待审批人决议 | `flow_node_instance.status` |
| `waiting_supplement` | 等待补件 | Waiting Supplement | 本节点请求了补件，暂停且**不可审批**；补件提交后回到 `active` | `flow_node_instance.status` |
| `approved` | 已通过 | Approved | 达到决议模式通过条件 | `flow_node_instance.status` |
| `rejected` | 已驳回 | Rejected | 任意候选人驳回（会签亦同）；同节点其余任务 → `auto_closed` | `flow_node_instance.status` |
| `skipped` | 已跳过 | Skipped | **仅事项单的②**：`involve_cost = false` 时跳过，不产生待办；轨迹留「本单不涉及费用，财务节点已跳过」 | `flow_node_instance.status` |
| `returned` | 已退回 | Returned | 被下一节点「回退上一节点」退回重审；**非终态**，上一节点重审通过后本节点回到 `active` | `flow_node_instance.status` |
| `cancelled` | 已取消 | Cancelled | 实例进入终态时，其余未完成节点统一取消 | `flow_node_instance.status` |

**联动约束**：任一节点实例 `rejected` → 实例 `rejected`、同节点其余任务 `auto_closed`、其余节点实例 `cancelled`。

---

## 6. 任务状态

**范围**：`flow_task.status`。会签 = 同一节点实例下多条任务。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `pending` | 待处理 | Pending | 待办列表的默认状态 | `flow_task.status` |
| `agreed` | 已同意 | Agreed | 审批人通过 | `flow_task.status` |
| `rejected` | 已拒绝 | Rejected | 审批人驳回；意见必填且 ≥5 字 | `flow_task.status` |
| `transferred` | 已转办 | Transferred | 审批人把本人任务转办他人；原审批人失去该任务，`origin_assignee_id` 留痕 | `flow_task.status` |
| `reassigned` | 已改派 | Reassigned | **仅系统管理员**可改派；用于人员离职、快照审批人不可用 | `flow_task.status` |
| `added_sign` | 已加签 | Added Sign | 本人动作被加签流程替代或已前加签；加签链记入 `add_sign_chain_json` | `flow_task.status` |
| `routed` | 已流转 | Routed | 审批人执行「流转」，指定下一承接部门 | `flow_task.status` |
| `rolled_back` | 已回退 | Rolled Back | 审批人执行「回退上一节点」 | `flow_task.status` |
| `supplement_requested` | 已请求补件 | Supplement Requested | 审批人要求补充材料；该任务**不可再审批** | `flow_task.status` |
| `auto_closed` | 已自动关闭 | Auto Closed | 同节点他人已决议、或单据已驳回/撤回/终止时系统关闭 | `flow_task.status` |

> 全部任务状态均为**终态**（不可从非 `pending` 回到 `pending`）；补件提交后**不复活旧任务**，而是回到请求补件的节点重新产生 `pending` 任务（或复用原任务并重置为 `pending`，由实现决定，但轨迹必须完整）。

---

## 7. 流转动作

**范围**：`flow_routing.action_type`。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `route` | 流转 | Route | 指定下一个承接部门，支持连续流转（A→B→C）；**禁止回流到已处理部门**；必须选择承接部门并填写原因 | `flow_routing.action_type` |
| `rollback` | 回退上一节点 | Roll Back | 退回上一个已完成节点重审；上一节点通过后自动回到本节点；同一节点最多被回退 2 次 | `flow_routing.action_type` |
| `back_home` | 回到本部门 | Back Home | 把后续流转收束回本部门；同一部门连续 ≤2 次；**不计入总次数（`routing_count`）**，但必须写入审计日志 | `flow_routing.action_type` |

**闸门约束（定稿）**：

| 约束 | 上限 | 计数位置 |
| --- | --- | --- |
| 流转 + 回退（`route` + `rollback`）总次数 | **≤ 5** | `flow_instance.routing_count` |
| 同一节点被回退次数 | **≤ 2** | `flow_node_instance.returned_count` |
| 回到本部门（`back_home`）连续次数 | **≤ 2** | 按 `flow_routing` 连续记录判定，**不计入 `routing_count`** |

---

## 8. 消息类型（站内信 / 邮件触发）

**范围**：`sys_message.msg_type`（站内信）；邮件由同一 `msg_type` 决定模板与收件人。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `todo` | 待办产生 | Todo Created | 新任务产生 | `sys_message.msg_type` |
| `rejected` | 被驳回 | Rejected | 单据被驳回，通知发起人 | `sys_message.msg_type` |
| `withdrawn` | 被撤回 | Withdrawn | 单据被发起人撤回 | `sys_message.msg_type` |
| `collaboration` | 协同任务 | Collaboration | ②勾选协同部门后，为协同部门负责人产生的并行子任务提示 | `sys_message.msg_type` |
| `timeout` | 超时催办 | Timeout Reminder | 节点超时或补件超时；**仅催办，不自动跳过、不自动升级** | `sys_message.msg_type` |
| `result` | 结果通知 | Result | 终审通过 / 终止 | `sys_message.msg_type` |
| `supplement` | 待补件 | Supplement Required | 审批人请求补充材料，通知发起人 | `sys_message.msg_type` |
| `cc` | 抄送 | Carbon Copy | 抄送人只读可见，**不产生待办** | `sys_message.msg_type` |

**渠道矩阵**：

| code | 站内信 | 邮件 | 抄送其上级（可配置） |
| --- | --- | --- | --- |
| `todo` | ✅ | ✅ | — |
| `rejected` | ✅ | ✅ | — |
| `withdrawn` | ✅ | — | — |
| `collaboration` | ✅ | — | — |
| `timeout` | ✅ | ✅ | ✅ |
| `result` | ✅ | ✅（终审通过） | — |
| `supplement` | ✅ | ✅ | — |
| `cc` | ✅ | — | — |

**协同任务的消息粒度（V0.4 定稿）**：②的审批人勾选 N 个协同部门 → 产生 **N 条 `collaboration` 站内信**（每个协同部门一条，收件人为该部门负责人）+ N 条 `todo` 待办；**不做多条合并为一条**，避免"多部门协同只提醒一次"造成漏审。

> 一期**无移动端推送通道**；邮件是唯一的主动提醒渠道。邮件发送失败不阻塞流程，失败记录可查。

---

## 9. 审批轨迹动作

**范围**：`sys_thread.action`（面向展示的轨迹）。**与 `sys_log.action`（审计）分工不同**：轨迹给人看，日志给审计看。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `approve` | 通过 | Approve | 节点/任务通过 | `sys_thread.action` |
| `reject` | 驳回 | Reject | 节点/任务驳回；意见 ≥5 字 | `sys_thread.action` |
| `route` | 流转 | Route | 流转给下一承接部门 | `sys_thread.action` |
| `rollback` | 回退上一节点 | Roll Back | 退回上一已完成节点 | `sys_thread.action` |
| `back_home` | 回到本部门 | Back Home | 流转收束回本部门 | `sys_thread.action` |
| `supplement_request` | 请求补件 | Supplement Request | 审批人要求补充材料 | `sys_thread.action` |
| `supplement_submit` | 提交补件 | Supplement Submit | 发起人提交补件（附件 + 补件说明） | `sys_thread.action` |
| `transfer` | 转办 | Transfer | 审批人转办本人任务 | `sys_thread.action` |
| `reassign` | 改派 | Reassign | 系统管理员改派 | `sys_thread.action` |
| `add_sign` | 加签 | Add Sign | 前加签 / 后加签 | `sys_thread.action` |
| `withdraw` | 撤回 | Withdraw | 发起人在②通过前撤回 | `sys_thread.action` |
| `terminate` | 终止 | Terminate | 管理员 / 集团分管领导终止 | `sys_thread.action` |
| `skip` | 跳过 | Skip | 节点被跳过（**仅事项单②：不涉及费用**）；轨迹留「本单不涉及费用，财务节点已跳过」 | `sys_thread.action` |

**补充值（与 `data-model.md` 6.5 对齐，纳入同一值域）**：

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `submit` | 提交 | Submit | 发起人提交（含驳回后重新提交） | `sys_thread.action` |
| `archive_register` | 归档登记 | Archive Registration | 节点⑦完成归档登记，**仅留痕、不产生审批决议**（V0.4 定稿；`data-model.md` 6.5 旧写 `archive`，见 S-14） | `sys_thread.action` |
| `cc` | 抄送 | Carbon Copy | 抄送记录 | `sys_thread.action` |

> 定义共 **13 + 3 = 16** 个值。轨迹**只追加、不可改、不可删**；重签或重审产生新记录，保留历史。

---

## 10. 事项类别与单据类型

### 10.1 事项类别（**配置项、五值、不参与路由**）

**范围**：`flow_instance.category`、`form_data.fields_json.category`、`sys_dict_item`（`dict_type = matter_category`）。种子数据见 `doc/dict-seed.md` §2。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `business` | 经营 | Business | 集团归口**恒为财务部** | `flow_instance.category` |
| `economy` | 经济 | Economy | 集团归口**恒为财务部** | `flow_instance.category` |
| `admin` | 行政 | Admin | 集团归口**恒为财务部** | `flow_instance.category` |
| `hr` | 人力 | HR | 集团归口**恒为财务部** | `flow_instance.category` |
| `invest` | 投资 | Investment | 集团归口**恒为财务部**；Q10 新增 | `flow_instance.category` |

**约束**：

1. 类别由管理后台数据字典维护，**可增删**；发起人选定后**任何节点不可改判**（改判只能驳回重提）。
2. 类别**不参与路由**、不影响审批人解析；集团归口节点恒为 `finance_review`。
3. 类别用于统计、检索、展示，以及集团分管领导的数据域匹配。
4. `flow_instance.category` 为**快照字段**，不随字典改名而变；字典改名只影响展示。

### 10.2 单据类型

**范围**：`flow_template.code`、`flow_template.form_type`、`form_data.form_type`、`flow_instance.form_type`。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `matter` | 事项审批单 | Matter Approval | 走完整主干；**唯一带「是否涉及费用」判断的单据** | `form_data.form_type` |
| `fund` | 资金审批单 | Fund Approval | 金额必填；`plan_category` / `payment_belong` **只存不用**；附件必填 | `form_data.form_type` |
| `contract` | 合同审批单 | Contract Approval | 须上传合同文本附件；归口仍为财务部 | `form_data.form_type` |
| `seal` | 印鉴证照审批单 | Seal and Certificate Approval | 须填使用期限与归还状态；**归还状态/归还日期为审批中唯一可改主字段** | `form_data.form_type` |

---

## 11. 字段类型（14 种）

**范围**：`form_schema_json.fields[].type`。与 `forms.md` 1.1 完全对齐，字段项结构契约见 `doc/templates.md` §2。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `text` | 单行文本 | Text | 受 `maxLength` 限制 | `form_schema_json.fields[].type` |
| `textarea` | 多行文本 | Textarea | 受 `maxLength` 限制；审批意见用同一控件 | `form_schema_json.fields[].type` |
| `number` | 数字 | Number | 整数或定点数，非金额 | `form_schema_json.fields[].type` |
| `amount` | 金额 | Amount | `DECIMAL(18,2)` 语义；**禁止浮点**；等宽右对齐 | `form_schema_json.fields[].type` |
| `select` | 单选下拉 | Select | 选项来源见 `options` / `optionsSource` | `form_schema_json.fields[].type` |
| `multiselect` | 多选 | Multi Select | 打印稿以 `☑ / ☐` 呈现 | `form_schema_json.fields[].type` |
| `date` | 日期 | Date | `YYYY-MM-DD` | `form_schema_json.fields[].type` |
| `daterange` | 日期区间 | Date Range | `[start, end]`，结束 ≥ 开始 | `form_schema_json.fields[].type` |
| `user` | 人员选择 | User Picker | 取通讯录；受数据域限制 | `form_schema_json.fields[].type` |
| `org` | 组织选择 | Org Picker | 四级组织级联；**无权限节点不渲染** | `form_schema_json.fields[].type` |
| `tag` | 标签 | Tag | 自由文本标签，去重 | `form_schema_json.fields[].type` |
| `boolean` | 布尔 | Boolean | 单勾选框；默认值 `false`（除非模板显式指定） | `form_schema_json.fields[].type` |
| `file` | 单个附件 | Single File | 受附件白名单与大小限制 | `form_schema_json.fields[].type` |
| `files` | 多个附件 | Multiple Files | 受附件白名单与数量限制 | `form_schema_json.fields[].type` |

> 需要「单选但打印为勾选框」的场景（`plan_category` / `payment_belong`），一期实现为 `boolean` 型 checkbox（见 `doc/dict-seed.md` §6–§7），不使用 `select`。

---

## 12. 附件格式与轮次

### 12.1 允许格式（15 种）

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `pdf` | PDF 文档 | PDF | 归档首选 | `flow_attachment.file_ext` |
| `doc` | Word 97-2003 | Word Legacy | — | `flow_attachment.file_ext` |
| `docx` | Word | Word | — | `flow_attachment.file_ext` |
| `xls` | Excel 97-2003 | Excel Legacy | — | `flow_attachment.file_ext` |
| `xlsx` | Excel | Excel | — | `flow_attachment.file_ext` |
| `ppt` | PowerPoint 97-2003 | PowerPoint Legacy | — | `flow_attachment.file_ext` |
| `pptx` | PowerPoint | PowerPoint | — | `flow_attachment.file_ext` |
| `jpg` | JPEG 图片 | JPEG Image | — | `flow_attachment.file_ext` |
| `jpeg` | JPEG 图片 | JPEG Image | 与 `jpg` 同义，均放行 | `flow_attachment.file_ext` |
| `png` | PNG 图片 | PNG Image | — | `flow_attachment.file_ext` |
| `zip` | ZIP 压缩包 | ZIP Archive | — | `flow_attachment.file_ext` |
| `rar` | RAR 压缩包 | RAR Archive | — | `flow_attachment.file_ext` |
| `7z` | 7-Zip 压缩包 | 7-Zip Archive | — | `flow_attachment.file_ext` |
| `heic` | HEIC 图片 | HEIC Image | V0.4 新增（E-07 已定稿）；**服务端须转 `jpg` 后预览** | `flow_attachment.file_ext` |
| `wps` | WPS 文档 | WPS Document | V0.4 新增（E-07 已定稿）；**不做在线预览，提示下载查看** | `flow_attachment.file_ext` |

**新增两种格式的降级口径（V0.4 定稿）**：

| 格式 | 预览策略 | 其他限制 |
| --- | --- | --- |
| `heic` | 服务端**转码为 `jpg`** 后提供预览缩略图；原文件保持 `heic` 不变、不覆盖 | 与 `jpg` / `jpeg` 同享 50MB 上限与附件计数 |
| `wps` | **不做在线预览**，列表与详情中提示「下载查看」 | 与 `doc` / `docx` 同享 50MB 上限与附件计数；不因放行而降低服务端 MIME 校验强度 |

### 12.2 禁止格式（9 种，上传即拒绝）

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `exe` | Windows 可执行文件 | Windows Executable | 上传即拒绝 | 上传校验 |
| `bat` | 批处理文件 | Batch Script | 上传即拒绝 | 上传校验 |
| `cmd` | 命令脚本 | Command Script | 上传即拒绝 | 上传校验 |
| `js` | JavaScript 脚本 | JavaScript | 上传即拒绝 | 上传校验 |
| `vbs` | VBScript 脚本 | VBScript | 上传即拒绝 | 上传校验 |
| `ps1` | PowerShell 脚本 | PowerShell Script | 上传即拒绝 | 上传校验 |
| `dll` | 动态链接库 | DLL | 上传即拒绝 | 上传校验 |
| `msi` | Windows 安装包 | MSI Installer | 上传即拒绝 | 上传校验 |
| `scr` | 屏幕保护程序 | Screen Saver | 上传即拒绝 | 上传校验 |

> 校验必须**扩展名 + MIME 双重判断**，二者任一命中黑名单即拒绝；存储为私有化本地路径，下载走鉴权接口，禁止公网直链。

### 12.3 附件轮次 `round` 语义

| 取值 | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `0` | 原始附件 | Original Attachment | 发起时上传，与任何补件无关 | `flow_attachment.round` |
| `1` | 第 1 次补件附件 | Supplement Round 1 | 与 `flow_supplement.supplement_round = 1` 对应 | `flow_attachment.round` |
| `2` | 第 2 次补件附件 | Supplement Round 2 | 与 `flow_supplement.supplement_round = 2` 对应 | `flow_attachment.round` |
| `3` | 第 3 次补件附件 | Supplement Round 3 | 与 `flow_supplement.supplement_round = 3` 对应；**全单补件上限即 3 次** | `flow_attachment.round` |

**约束**：

| 项目 | 规则 |
| --- | --- |
| 单文件大小 | ≤ 50 MB |
| 单次上传数量 | ≤ 20 个 |
| 单张单据附件总数 | ≤ 50 个（**含补件**） |
| 补件可写字段 | **仅附件与补件说明**；金额、对方主体、事项类别等主字段一律只读 |
| 补件不算驳回 | 不写驳回记录、不计入驳回率、不改变 `flow_instance.status` |
| 补件时限 | 默认 3 个工作日；超时**仅催办发起人**，不自动驳回、不自动通过 |

---

## 13. 日志类别与保留期

**范围**：`sys_log`（审计日志）、`sys_thread`（审批轨迹）、`sys_login_log`（登录日志）、`flow_signature`（签名记录）。

| code | 中文名 | 英文名 | 说明 | 出现位置（表/字段） |
| --- | --- | --- | --- | --- |
| `operation` | 操作日志 | Operation Log | 谁、何时、从何 IP、对哪个单据/配置、执行了什么操作 | `sys_log.action` + `target_type` |
| `approval_thread` | 审批轨迹 | Approval Thread | 每个节点的审批人、意见、签名图、时间、决议模式下的各人结论 | `sys_thread.action` |
| `signature` | 签名记录 | Signature Record | 签名图、时间戳、审批人 ID、设备指纹、IP，与单据绑定存储；**不可修改、不可删除**，仅可重新签署并保留历史版本 | `flow_signature` |
| `permission_change` | 权限变更日志 | Permission Change Log | 角色、数据域、权限树勾选、流程模板发布的所有变更（含变更前后值） | `sys_log.before_json` / `after_json` |
| `login` | 登录日志 | Login Log | 登录时间、IP、设备信息、失败原因 | `sys_login_log` |

**保留期与不可篡改性**：

| 类别 | 保留期 | 不可篡改要求 | 归档处置 |
| --- | --- | --- | --- |
| 操作日志（审计日志） | **≥ 10 年** | 只允许追加，禁止修改与删除（应用层禁用 + 数据库触发器双重强制） | 随单归档仍只读 |
| 审批轨迹 | **≥ 10 年** | 只允许追加 | 随单归档仍只读 |
| 签名记录 | **永不删除**（10 年保留期内哈希可校验） | 只追加；重新签署产生新记录，不覆盖 | 随单迁移至历史库，不清理 |
| 权限变更日志 | **≥ 10 年** | 只允许追加；变更前后值必填 | 不归档 |
| 登录日志 | **1 年** | 只允许追加 | 到期可清理（一期不做物理删除） |

> **保留期依据（V0.4 定稿）**：按 **PRD REQ-NFR-007** 执行——审计日志与审批轨迹 ≥10 年、登录日志 1 年、签名记录永不删除。**已确认，无需法务复审。**

> 哈希计算范围必须**包含 CA 预留字段**（`ca_signature` / `ca_cert_serial` / `ca_issuer` / `tsa_source` / `verify_result` / `verified_at`），以便二期接入第三方 CA 时无需重建历史签名。

---

## 14. 迁移对照表（旧值 → 本文档定稿）

供 `data-model.md` 建库脚本与存量数据迁移使用。**旧值一律标注废弃，不得在新数据中出现。**

| 值域 | 旧值（V0.3 及既有文档） | 定稿值 | 迁移动作 |
| --- | --- | --- | --- |
| `flow_instance.category` | `operate` | `business` | `UPDATE flow_instance SET category='business' WHERE category='operate'` |
| `sys_dict_item.dict_type` | `category` | `matter_category` | 改 `dict_type`；同时把 `item_code='operate'` 改为 `business` |
| `sys_dict_item.dict_type` | `pay_method` | `payment_method` | 改 `dict_type` |
| `sys_dict_item.dict_type` | `cert_name` | `cert_type` | 改 `dict_type` |
| `flow_node.node_code` | `department` | `finance_review` | 按 seq 映射替换 |
| `flow_node.node_code` | `finance` | `finance_review` | 与上一条合并（**财务部只审一次**） |
| `flow_node.node_code` | `company_exec` | `branch_leader` | 替换 |
| `flow_node.node_code` | `gm` | `subsidiary_gm` | 替换 |
| `flow_node.node_code` | `group_dept` | `finance_review` | 与 ② 合并（集团层不再有独立归口节点） |
| `flow_node.node_code` | `group_exec` | `group_leader` | 替换 |
| `flow_node.node_code` | `archive` | `archive_register` | 替换 |
| `flow_node.approver_rule` | `department_leader` | `dept_leader_upward` | 替换 |
| `flow_node.approver_rule` | `department` | `finance_owner` | 替换 |
| `flow_instance.sub_status` | `supplement` | `pending_supplement` | 替换 |
| `flow_node_instance.status` | `processing` | `active` | 替换 |
| `flow_node_instance.status` | `awaiting_supplement` | `waiting_supplement` | 替换 |
| `flow_task.status` | `returned` | `rolled_back` | 替换 |
| `flow_task.status` | `supplement` | `supplement_requested` | 替换 |
| `flow_task.status` | `closed` | `auto_closed` | 替换 |
| `flow_routing.action_type` | `return_node` | `rollback` | 替换 |
| `sys_thread.action` | `addsign` | `add_sign` | 替换 |
| `sys_thread.action` | `return_node` | `rollback` | 替换 |
| `sys_thread.action` | `archive` | `archive_register` | 替换（⑦留痕动作，与节点码同名） |
| `form_data.fields_json` 键名 | `planned_category` | `plan_category` | 字段 code 改名（JSON 路径迁移；`forms.md` 口径） |
| `form_data.fields_json` 键名 | `review_dept_other`（字段） | `other_review_depts`（字段） | 字段 code 改名；**字典类型 `review_dept_other` 不变** |
| `sys_message.msg_type` | （无） | `collaboration` | 新增，无存量数据 |

---

## 15. 变更记录

| 版本 | 日期 | 修改说明 |
| --- | --- | --- |
| V0.4 | 2026-07-09 | 首版：确立 7 节点主干节点码与 9 条审批人解析规则码；统一状态机命名；流转动作改 `rollback`；新增 `collaboration` 消息类型；新增轨迹动作 `skip`；明确「已驳回/已撤回可回到草稿」；明确事项类别五值不参与路由；明确枚举 vs 字典边界；给出旧值迁移对照表 |
| V0.4（业务裁定后修订） | 2026-07-09 | §0.2 待业务确认项**全部关闭**（E-01–E-07 定稿），新增 §0.2.1 技术执行项；⑦`archive_register` 明确「仅登记不审批、无决议模式与阈值、不计入效率统计」，轨迹动作 `archive` → `archive_register`；协同任务按**每个协同部门一条站内信**；附件允许格式 13 → **15 种**（放行 `heic` / `wps` + 预览降级口径）；保留期改为按 **PRD REQ-NFR-007** 执行、无需法务复审；字段 code 统一为 `plan_category` 与 `other_review_depts`；新增 S-14 / S-15 差异与同步状态核对 |

---

> **交叉引用**：字典种子数据见 [`dict-seed.md`](dict-seed.md)；流程与表单模板契约见 [`templates.md`](templates.md)；表结构以 [`data-model.md`](data-model.md) 为准；行为语义以 [`prd-0.1.md`](prd-0.1.md) 为准；视觉与打印规格以 [`DESIGN.md`](../DESIGN.md) 为准。
