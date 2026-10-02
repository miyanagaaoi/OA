# 集团OA审批系统 · 流程与表单模板契约（templates.md）

| 项目 | 内容 |
| --- | --- |
| 文档用途 | 定义**流程模板**（节点配置）与**表单模板**（`form_schema_json`）的结构契约、版本与快照规则、变更流程，以及模板字段与打印规格的对应关系 |
| 权威边界 | **取值 code** 一律引用 [`enums.md`](enums.md)；**表结构**以 [`data-model.md`](data-model.md) §4 为准；**字段业务含义与校验口径**以 [`forms.md`](forms.md) 为准；**打印视觉规格**以 [`DESIGN.md`](../DESIGN.md)「打印规格」为准。本文档定义的是**模板结构契约**（JSON 形状与节点配置口径） |
| 与 PRD 的关系 | 承接 PRD 6.3（主干 7 节点）、6.4（REQ-FLOW-002/003/004/006/008）、6.5（REQ-SIGN-003）、6.2（REQ-FORM-001 打印） |
| 与 dict-seed.md 的关系 | `form_schema_json.fields[].optionsSource.dictType` 必须存在于 [`dict-seed.md`](dict-seed.md) 的字典白名单 |
| 版本 | **V0.4 配套** · 状态：评审稿 · 基准日期：2026-07-09 |
| 契约效力 | 本文档的 JSON 示例**语法正确、可直接使用**；所有中文说明一律置于代码块之外，代码块内为纯 JSON，不含注释 |

---

## 0. 定稿默认值（原「待业务确认项」，**全部已关闭**）

> 下表为 **V0.4 定稿默认值**（业务已裁定，直接作为种子模板发布；后台仍可调整）。**业务侧无未决项**；仅剩技术执行项，见 §0.1。

| 编号 | 事项 | V0.4 定稿默认值 | 影响面 |
| --- | --- | --- | --- |
| T-01 | 各节点超时时长 | **② 财务部复核 = 48h；其余节点（①③④⑤⑥⑦）= 24h**（后台可改，最小 24h；PRD 9.1 的平台默认是「无，须显式配置，且 ≥24h」，种子模板给出显式值） | `flow_node.timeout_hours` |
| T-02 | 流转/回退能力开启范围 | **仅 ②⑤⑥ 开启**（集团层）；①③④⑦ 关闭 | `flow_node.allow_route` |
| T-03 | 归档登记节点是否「仅登记不审批」 | **默认「仅登记不审批」**：不产生审批决议、不需要决议模式与阈值、不计入审批时长与效率统计，仅留痕（`sys_thread.action = archive_register`）；`sign_policy = none`；**可按模板配置改为需审批** | ⑦ 的 `sign_policy` / `decision_mode` |
| T-04 | ③④ 是否允许加签 | **允许**（`allow_add_sign = true`）；⑦ 不允许 | ③④ 的 `allow_add_sign` |
| T-05 | 印鉴证照单在子公司层的打印版式 | 按「印鉴用印鉴证照版式」优先（见 §5 优先级规则） | 打印版式选择 |
| T-06 | 合同单「其他会审部门」字段是否保留 | **保留**（字段 `other_review_depts`，字典类型 `review_dept_other`）；不保留时删除字段项即可隐藏该行 | `form_schema_json` + 打印版式 |
| T-07 | 会签阈值缺省口径 | `pass_threshold = NULL` 视为**过半**；百分比**向上取整**；百分比与绝对人数同时存在时**绝对人数优先** | `flow_node.pass_threshold` |
| T-08 | 自由跳转是否开放 | **一期全部节点默认关闭**（`allow_jump = false`），**仅管理员显式开启的节点可用**，跳转必须填原因并留痕 | `flow_node.allow_jump` |

### 0.1 技术执行项（非业务决策）

| 编号 | 技术项 | 待办 | 责任面 |
| --- | --- | --- | --- |
| B-01 | ⑦ 的 `decision_mode` 可空 | 登记节点无决议，`decision_mode` / `pass_threshold` 置 `NULL`；`data-model.md` 4.2 需允许 `node_type = 'archive'` 时为 NULL（或实现写 `'any'` 占位、引擎跳过决议计算） | 后端 + `data-model.md` |
| B-02 | 「不计入审批时长与效率统计」的口径 | 效率报表（PRD REQ-ADMIN-005，P1）的分母与耗时统计需**排除 `node_type = 'archive'` 的节点** | 报表实现 |
| B-03 | `archive_register` 轨迹动作 | `data-model.md` 6.5 现写 `archive`，须同步为 `archive_register`（见 `enums.md` S-14） | `data-model.md` |
| B-04 | `heic` / `wps` 预览降级 | 附件白名单 15 种；`heic` 转 `jpg` 预览、`wps` 提示下载 | 前端 + 文件服务 |

---

## 1. 四类单据 × 7 节点流程模板配置表

### 1.0 通用口径

| 项目 | 口径 |
| --- | --- |
| 主干节点数 | **固定 7 个**（①–⑦），顺序固定；一期无条件路由、金额不参与路由 |
| 协同 / 会签 | 是**②的并行子任务组**，不占主链编号；由②的审批人在审批时勾选协同部门，每个被勾选部门取该部门负责人（`collab_dept_leader`），**全部完成后**才进入③ |
| 可跳过的节点 | **仅事项单的②**（`involve_cost = false` 时）；跳过时节点实例状态 `skipped`，但单据归口部门**仍记为财务部** |
| 默认强制签名 | **⑤ 集团分管领导**、**⑥ 集团董事长**（`sign_policy = required`）；其余节点默认 `optional`，⑦ 默认 `none` |
| 决议模式 | 审批节点（①–⑥）默认 `any`（或签）；如需会签，逐节点改 `all` 并配置 `pass_threshold`。**⑦ 归档登记为登记节点：不适用决议模式，`decision_mode` 与 `pass_threshold` 均为 `NULL`**，不产生审批决议、不计入审批时长与效率统计，仅留痕（`sys_thread.action = archive_register`） |
| 阈值规则 | 支持百分比（如 `66%`）与绝对人数（如 `2`）；**绝对人数优先**；`NULL` 视为过半；百分比**向上取整** |
| 超时规则 | **仅催办**（站内信 + 邮件），**不自动跳过、不自动升级**；最小可配置 24h。**V0.4 定稿默认值：② = 48h，其余节点（①③④⑤⑥⑦）= 24h**（后台可改，见 §0 T-01） |
| 自由跳转 | **V0.4 定稿默认值：全部节点 `allow_jump = false`**（关闭）；仅管理员显式开启的节点可用，且必须填写原因并计入审计日志与审批轨迹（见 §0 T-08） |
| 闸门 | 流转 + 回退总次数 ≤5；同一节点被回退 ≤2；回到本部门连续 ≤2（不计入总次数）；补件同节点 ≤1 / 全单 ≤3 |
| 模板版本 | 四类单据各自独立模板，`flow_template.code` = 单据类型码，起始 `version = 1` |

### 1.1 事项审批单（`template.code = matter`）

| seq | node_code | node_name | approver_rule | decision_mode | pass_threshold | sign_policy | timeout_hours | allow_addsign | allow_jump | allow_route | 跳过条件 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `dept_leader` | 直属部门负责人 | `dept_leader_upward` | `any` | NULL | `optional` | 24 | true | false | false | — |
| **2** | `finance_review` | 财务部复核（= 集团归口） | `finance_owner` | `any` | NULL | `optional` | **48** | true | false | **true** | **`involve_cost = false` 时跳过（唯一可跳过节点）** |
| 3 | `branch_leader` | 分公司分管领导 | `branch_leader` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 4 | `subsidiary_gm` | 子公司总经理 | `subsidiary_gm` | `any` | NULL | `optional` | 24 | true | false | false | — |
| **5** | `group_leader` | 集团分管领导 | `group_leader` | `any` | NULL | **`required`** | 24 | true | false | **true** | — |
| **6** | `chairman` | 集团董事长 | `chairman` | `any` | NULL | **`required`** | 24 | true | false | **true** | — |
| 7 | `archive_register` | 归档登记 | `designated` | —（不适用） | —（不适用） | `none` | 24 | false | false | false | — |

- **超时默认值（V0.4 定稿，T-01）**：**② = 48h**，其余节点（①③④⑤⑥⑦）**= 24h**；后台可改，最小 24h；超时**仅催办**，不自动跳过、不升级。
- **协同子任务组**：挂在 seq = 2 之下，`approver_rule = collab_dept_leader`，每个协同部门一组独立任务、**每个协同部门一条站内信**；任一组驳回则单据驳回。
- **⑦ 参数**：`approver_param = {"role_code": "finance_clerk"}`（财务部内勤角色，见 `enums.md` E-02）。⑦ **默认「仅登记不审批」**（T-03）：`sign_policy = none`、`decision_mode` / `pass_threshold` 为 `NULL`（登记节点无决议），**不产生审批决议、不计入审批时长与效率统计**，仅写 `sys_thread` 留痕（`action = archive_register`）；可按模板配置改为需审批。
- **二次确认口径**：即便②被跳过，`flow_instance` 的归口部门字段仍为财务部，统计与审计口径不变。

### 1.2 资金审批单（`template.code = fund`）

| seq | node_code | node_name | approver_rule | decision_mode | pass_threshold | sign_policy | timeout_hours | allow_addsign | allow_jump | allow_route | 跳过条件 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `dept_leader` | 直属部门负责人 | `dept_leader_upward` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 2 | `finance_review` | 财务部复核（= 集团归口） | `finance_owner` | `any` | NULL | `optional` | **48** | true | false | `true` | **恒为「涉及」——不可跳过** |
| 3 | `branch_leader` | 分公司分管领导 | `branch_leader` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 4 | `subsidiary_gm` | 子公司总经理 | `subsidiary_gm` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 5 | `group_leader` | 集团分管领导 | `group_leader` | `any` | NULL | **`required`** | 24 | true | false | `true` | — |
| 6 | `chairman` | 集团董事长 | `chairman` | `any` | NULL | **`required`** | 24 | true | false | `true` | — |
| 7 | `archive_register` | 归档登记 | `designated` | —（不适用） | —（不适用） | `none` | 24 | false | false | false | — |

- 超时默认值同 §1.1（② = 48h，其余 24h）；⑦ 为登记节点，`decision_mode` / `pass_threshold` 为 `NULL`。
- 金额必填（`amount`），附件必填（≥1）；`plan_category` / `payment_belong` **只存不用**，不得出现在任何 `skip_condition` 中。

### 1.3 合同审批单（`template.code = contract`）

| seq | node_code | node_name | approver_rule | decision_mode | pass_threshold | sign_policy | timeout_hours | allow_addsign | allow_jump | allow_route | 跳过条件 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `dept_leader` | 直属部门负责人 | `dept_leader_upward` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 2 | `finance_review` | 财务部复核（= 集团归口） | `finance_owner` | `any` | NULL | `optional` | **48** | true | false | `true` | **恒为「涉及」——不可跳过** |
| 3 | `branch_leader` | 分公司分管领导 | `branch_leader` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 4 | `subsidiary_gm` | 子公司总经理 | `subsidiary_gm` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 5 | `group_leader` | 集团分管领导 | `group_leader` | `any` | NULL | **`required`** | 24 | true | false | `true` | — |
| 6 | `chairman` | 集团董事长 | `chairman` | `any` | NULL | **`required`** | 24 | true | false | `true` | — |
| 7 | `archive_register` | 归档登记 | `designated` | —（不适用） | —（不适用） | `none` | 24 | false | false | false | — |

- 超时默认值同 §1.1（② = 48h，其余 24h）；⑦ 为登记节点，`decision_mode` / `pass_threshold` 为 `NULL`。
- 合同类型为「其他」时 `contract_type_other` 条件必填；须上传合同文本附件（≥1）。
- 若启用「其他会审部门」字段（**字段 code `other_review_depts`**，字典类型 `review_dept_other`），其值**不改变流程结构**——②审批人可在审批时据此勾选协同部门，作为②的并行子任务组。

### 1.4 印鉴证照审批单（`template.code = seal`）

| seq | node_code | node_name | approver_rule | decision_mode | pass_threshold | sign_policy | timeout_hours | allow_addsign | allow_jump | allow_route | 跳过条件 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `dept_leader` | 直属部门负责人 | `dept_leader_upward` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 2 | `finance_review` | 财务部复核（= 集团归口） | `finance_owner` | `any` | NULL | `optional` | **48** | true | false | `true` | **恒为「涉及」——不可跳过** |
| 3 | `branch_leader` | 分公司分管领导 | `branch_leader` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 4 | `subsidiary_gm` | 子公司总经理 | `subsidiary_gm` | `any` | NULL | `optional` | 24 | true | false | false | — |
| 5 | `group_leader` | 集团分管领导 | `group_leader` | `any` | NULL | **`required`** | 24 | true | false | `true` | — |
| 6 | `chairman` | 集团董事长 | `chairman` | `any` | NULL | **`required`** | 24 | true | false | `true` | — |
| 7 | `archive_register` | 归档登记 | `designated` | —（不适用） | —（不适用） | `none` | 24 | false | false | false | — |

- 超时默认值同 §1.1（② = 48h，其余 24h）；⑦ 为登记节点，`decision_mode` / `pass_threshold` 为 `NULL`。
- **三态例外**：`return_status` / `return_date` 在审批中可由**发起人与节点⑦**修改，其余主字段一律只读。该白名单由模板字段的 `readonlyAfterSubmit` + 服务端字段级白名单共同实现（见 §2.3）。

### 1.5 四类模板差异摘要

| 差异项 | matter | fund | contract | seal |
| --- | --- | --- | --- | --- |
| ② 是否可跳过 | **可**（`involve_cost = false`） | 否 | 否 | 否 |
| ② 的 `skip_condition` | `{"field":"involve_cost","op":"eq","value":false}` | `null` | `null` | `null` |
| ①③④ 配置 | 完全相同 | 完全相同 | 完全相同 | 完全相同 |
| ⑤⑥ 配置 | 完全相同（强制签名） | 完全相同 | 完全相同 | 完全相同 |
| ⑦ 配置 | 完全相同（仅登记） | 完全相同 | 完全相同 | 完全相同（额外承担归还状态登记） |
| 超时 `timeout_hours` | ② = **48h**，其余节点 **24h** | 同左 | 同左 | 同左 |
| 表单字段 | **9 个**（含条件字段） | **12 个** | **14 个** | **12 个** |
| 打印版式 | 内部审批单版式 | 集团层集团版式 / 子公司层内部审批单版式 | 集团层集团版式 / 子公司层内部审批单版式 | 印鉴证照版式 |

### 1.6 `flow_node` 种子数据（事项单，可直接使用）

```json
{
  "template": {
    "code": "matter",
    "name": "事项审批单流程",
    "form_type": "matter",
    "version": 1,
    "status": "draft",
    "node_count": 7
  },
  "nodes": [
    {
      "seq": 1,
      "node_code": "dept_leader",
      "name": "直属部门负责人",
      "node_type": "approve",
      "approver_rule": "dept_leader_upward",
      "approver_param": null,
      "decision_mode": "any",
      "pass_threshold": null,
      "sign_policy": "optional",
      "timeout_hours": 24,
      "allow_add_sign": true,
      "allow_jump": false,
      "allow_route": false,
      "skip_condition": null
    },
    {
      "seq": 2,
      "node_code": "finance_review",
      "name": "财务部复核",
      "node_type": "approve",
      "approver_rule": "finance_owner",
      "approver_param": null,
      "decision_mode": "any",
      "pass_threshold": null,
      "sign_policy": "optional",
      "timeout_hours": 48,
      "allow_add_sign": true,
      "allow_jump": false,
      "allow_route": true,
      "skip_condition": { "field": "involve_cost", "op": "eq", "value": false }
    },
    {
      "seq": 3,
      "node_code": "branch_leader",
      "name": "分公司分管领导",
      "node_type": "approve",
      "approver_rule": "branch_leader",
      "approver_param": null,
      "decision_mode": "any",
      "pass_threshold": null,
      "sign_policy": "optional",
      "timeout_hours": 24,
      "allow_add_sign": true,
      "allow_jump": false,
      "allow_route": false,
      "skip_condition": null
    },
    {
      "seq": 4,
      "node_code": "subsidiary_gm",
      "name": "子公司总经理",
      "node_type": "approve",
      "approver_rule": "subsidiary_gm",
      "approver_param": null,
      "decision_mode": "any",
      "pass_threshold": null,
      "sign_policy": "optional",
      "timeout_hours": 24,
      "allow_add_sign": true,
      "allow_jump": false,
      "allow_route": false,
      "skip_condition": null
    },
    {
      "seq": 5,
      "node_code": "group_leader",
      "name": "集团分管领导",
      "node_type": "approve",
      "approver_rule": "group_leader",
      "approver_param": null,
      "decision_mode": "any",
      "pass_threshold": null,
      "sign_policy": "required",
      "timeout_hours": 24,
      "allow_add_sign": true,
      "allow_jump": false,
      "allow_route": true,
      "skip_condition": null
    },
    {
      "seq": 6,
      "node_code": "chairman",
      "name": "集团董事长",
      "node_type": "approve",
      "approver_rule": "chairman",
      "approver_param": null,
      "decision_mode": "any",
      "pass_threshold": null,
      "sign_policy": "required",
      "timeout_hours": 24,
      "allow_add_sign": true,
      "allow_jump": false,
      "allow_route": true,
      "skip_condition": null
    },
    {
      "seq": 7,
      "node_code": "archive_register",
      "name": "归档登记",
      "node_type": "archive",
      "approver_rule": "designated",
      "approver_param": { "role_code": "finance_clerk" },
      "decision_mode": null,
      "pass_threshold": null,
      "sign_policy": "none",
      "timeout_hours": 24,
      "allow_add_sign": false,
      "allow_jump": false,
      "allow_route": false,
      "skip_condition": null
    }
  ]
}
```

**其他三类模板的差异**：仅把 seq = 2 节点的 `skip_condition` 改为 `null`，`template.code` / `name` / `form_type` 改为对应单据类型；其余节点配置**逐字相同**。

> **本段 JSON 的定稿要点（V0.4）**：① `timeout_hours` 按 T-01 定稿——**② = 48，其余节点 = 24**；② seq = 7 的 `decision_mode` 与 `pass_threshold` 均为 **`null`**（登记节点无决议，见 B-01）；③ `allow_route` 仅 ②⑤⑥ 为 `true`；④ 全部节点 `allow_jump = false`（T-08）；⑤ ⑦ 的留痕动作在轨迹中记为 `archive_register`。

**协同子任务组的存储**：不新增 `flow_node` 行。运行时在 `flow_node_instance` 中新增 `node_seq = 2` + 不同 `dept_id` 的行（唯一键 `(instance_id, node_seq, dept_id)`），`approver_ids_json` 存该协同部门负责人快照；**每个协同部门一条 `collaboration` 站内信**。

---

## 2. `form_schema_json` 结构契约

### 2.1 顶层结构

| 键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `form_type` | string | 是 | 单据类型码，取值见 `enums.md` §10.2 |
| `template_code` | string | 是 | 与 `flow_template.code` 一致 |
| `schema_version` | integer | 是 | **必须等于发布时的 `flow_template.version`**（一期表单与流程共用同一版本号，同一次发布同时升版） |
| `published_at` | string | 否 | ISO 8601 UTC 时间 |
| `sections` | array | 否 | 字段分组（驱动界面的「分区标题带」与打印稿的分组标题行）；缺省时按 `fields` 顺序单段渲染 |
| `fields` | array | 是 | 字段项数组，**顺序即界面与打印稿的字段顺序** |

`sections[]` 项结构：

| 键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 分组 id |
| `title` | string | 是 | 界面分组标题 |
| `printTitle` | string | 否 | 打印稿分组标题；缺省沿用 `title` |
| `collapsible` | boolean | 否 | 界面是否可折叠，默认 `true` |
| `fields` | array&lt;string&gt; | 是 | 该分组包含的字段 `code` 列表 |

### 2.2 字段项结构

| 键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 是 | 字段 id，小写蛇形；写入 `form_data.fields_json` 的键名；**一经使用不得复用** |
| `label` | string | 是 | 界面显示名 |
| `printLabel` | string | 是 | **打印标签**；缺省沿用 `label`（为保持契约显式，本契约要求必填）。映射依据见 `forms.md` 第 10 节 |
| `printVisible` | boolean | 是 | 是否在打印稿占用**独立字段行**；`false` 不等于「完全不输出」，仍可被打印版式的固定段落引用（见 §5.2） |
| `type` | string | 是 | 字段类型，14 种之一，见 `enums.md` §11 |
| `required` | boolean | 是 | 是否无条件必填；条件必填写入 `rules` 的 `conditionalRequired` 并同步 `linkage.requiredWhen` |
| `rules` | array | 是 | 校验规则数组；无条件必填时可为空数组，不得为 `null` |
| `maxLength` | integer | 是 | 文本最大字符数；非文本类型填 `0` 表示不适用 |
| `options` | array | 否 | **内联静态选项**（`checkbox` / `select` / `multiselect`）；与 `optionsSource` 互斥 |
| `linkage` | object | 否 | 联动定义：`visibleWhen` / `requiredWhen` / `clearWhen` |
| `readonlyAfterSubmit` | boolean | 是 | 提交发起后是否只读；`false` 仅允许用于**服务端字段级白名单**内的字段（一期仅 `return_status` / `return_date` / 补件字段） |

**本契约允许的扩展键**（不在上述列表，但为标准扩展，实现必须支持）：

| 键 | 类型 | 说明 |
| --- | --- | --- |
| `defaultValue` | 任意 | 预填值；`boolean` 用 `true` / `false`，`select` 用 option code |
| `optionsSource` | object | 字典驱动选项：`{ "kind": "dict", "dictType": "matter_category" }`；`dictType` 必须在 `dict-seed.md` §0.3 白名单内 |
| `placeholder` | string | 输入提示 |
| `unit` | string | 单位（如「万元」「天」） |

`options[]` 项结构：`{ "code": string, "label": string, "enabled": boolean, "sortNo": integer }`。

`linkage` 条件对象：`{ "field": string, "op": string, "value": 任意 }`；`op` 取值 `eq` / `ne` / `in` / `notIn` / `gt` / `gte` / `lt` / `lte` / `empty` / `notEmpty` / `checked`。多条件「与」关系用 `{ "all": [ 条件, ... ] }`，或关系用 `{ "any": [ 条件, ... ] }`。

### 2.3 `rules[]` 项结构（规则类型表）

| `type` | 适用字段类型 | 参数 | 说明 |
| --- | --- | --- | --- |
| `maxLength` | text / textarea | `value` | 字符数上限 |
| `minLength` | text / textarea | `value` | 字符数下限 |
| `amountRange` | amount | `min` / `max` / `scale` | 金额以**字符串**形式的定点数表达，禁止浮点；`scale = 2` |
| `numberRange` | number | `min` / `max` / `integer` | `integer = true` 表示整数 |
| `dateNotBefore` | date / daterange | `value`：`today` / `submit_date` / ISO 日期 | 日期下限 |
| `dateNotBeforeField` | date | `field` | 不早于另一日期字段（如 `period_end ≥ period_start`） |
| `pattern` | text | `value`（正则） / `flags` | 格式校验（如统一社会信用代码 18 位） |
| `filePolicy` | file / files | `maxSizeMb` / `maxCount` / `allowExt` / `denyExt` | 附件白名单与大小限制；缺省取全局规则（50MB、20 个、**15 种格式**，见 `enums.md` §12.1） |
| `pickerLimit` | user / org / tag | `max` | 选择数量上限 |
| `orgScope` | org | `value`：`initiator_company_subtree` 等 | 组织选择范围限制 |
| `conditionalRequired` | 任意 | `when`（条件对象） | 条件必填 |
| `inDict` | select / multiselect | `value`：`dictType` | 取值必须属于该字典 |
| `unique` | text | `scope` | 唯一性校验（如关联合同单号必须存在） |

所有规则项都支持 `message`（违反时的中文提示文案）。

### 2.4 最小可用完整示例：事项审批单

```json
{
  "form_type": "matter",
  "template_code": "matter",
  "schema_version": 3,
  "published_at": "2026-07-09T10:00:00Z",
  "sections": [
    {
      "id": "basic",
      "title": "基本信息",
      "printTitle": "事项基本信息",
      "collapsible": true,
      "fields": ["title", "category", "description"]
    },
    {
      "id": "cost",
      "title": "费用信息",
      "printTitle": "资金审批内容",
      "collapsible": true,
      "fields": ["involve_cost", "amount", "cost_bearer"]
    },
    {
      "id": "other",
      "title": "其他信息",
      "printTitle": "其他信息",
      "collapsible": true,
      "fields": ["expect_date", "cc_users", "attachments"]
    }
  ],
  "fields": [
    {
      "code": "title",
      "label": "事项标题",
      "printLabel": "事项标题",
      "printVisible": true,
      "type": "text",
      "required": true,
      "rules": [
        { "type": "maxLength", "value": 60, "message": "事项标题不能超过 60 个字符" }
      ],
      "maxLength": 60,
      "placeholder": "请简要填写事项标题",
      "readonlyAfterSubmit": true
    },
    {
      "code": "category",
      "label": "事项类别",
      "printLabel": "事项分类",
      "printVisible": true,
      "type": "select",
      "required": true,
      "rules": [
        { "type": "inDict", "value": "matter_category", "message": "事项类别取值非法" }
      ],
      "maxLength": 0,
      "optionsSource": { "kind": "dict", "dictType": "matter_category" },
      "readonlyAfterSubmit": true
    },
    {
      "code": "description",
      "label": "事项描述",
      "printLabel": "事项描述",
      "printVisible": true,
      "type": "textarea",
      "required": true,
      "rules": [
        { "type": "minLength", "value": 10, "message": "事项描述至少 10 个字符" },
        { "type": "maxLength", "value": 2000, "message": "事项描述不能超过 2000 个字符" }
      ],
      "maxLength": 2000,
      "readonlyAfterSubmit": true
    },
    {
      "code": "involve_cost",
      "label": "是否涉及费用",
      "printLabel": "是否涉及费用",
      "printVisible": false,
      "type": "boolean",
      "required": true,
      "rules": [],
      "maxLength": 0,
      "defaultValue": false,
      "readonlyAfterSubmit": true
    },
    {
      "code": "amount",
      "label": "涉及金额",
      "printLabel": "涉及金额",
      "printVisible": true,
      "type": "amount",
      "required": false,
      "rules": [
        {
          "type": "conditionalRequired",
          "when": { "field": "involve_cost", "op": "eq", "value": true },
          "message": "涉及费用时，涉及金额为必填"
        },
        {
          "type": "amountRange",
          "min": "0.01",
          "max": "99999999999.99",
          "scale": 2,
          "message": "金额必须大于 0 且最多两位小数"
        }
      ],
      "maxLength": 0,
      "linkage": {
        "visibleWhen": { "field": "involve_cost", "op": "eq", "value": true },
        "requiredWhen": { "field": "involve_cost", "op": "eq", "value": true },
        "clearWhen": { "field": "involve_cost", "op": "eq", "value": false }
      },
      "readonlyAfterSubmit": true
    },
    {
      "code": "cost_bearer",
      "label": "费用承担主体",
      "printLabel": "费用承担主体",
      "printVisible": false,
      "type": "org",
      "required": false,
      "rules": [
        {
          "type": "conditionalRequired",
          "when": { "field": "involve_cost", "op": "eq", "value": true },
          "message": "涉及费用时，费用承担主体为必填"
        },
        {
          "type": "orgScope",
          "value": "initiator_company_subtree",
          "message": "费用承担主体限本公司及以下节点"
        }
      ],
      "maxLength": 0,
      "defaultValue": "initiator_company",
      "linkage": {
        "visibleWhen": { "field": "involve_cost", "op": "eq", "value": true },
        "requiredWhen": { "field": "involve_cost", "op": "eq", "value": true },
        "clearWhen": { "field": "involve_cost", "op": "eq", "value": false }
      },
      "readonlyAfterSubmit": true
    },
    {
      "code": "expect_date",
      "label": "期望完成日期",
      "printLabel": "期望完成日期",
      "printVisible": true,
      "type": "date",
      "required": false,
      "rules": [
        { "type": "dateNotBefore", "value": "today", "message": "期望完成日期不能早于今天" }
      ],
      "maxLength": 0,
      "readonlyAfterSubmit": true
    },
    {
      "code": "cc_users",
      "label": "抄送人",
      "printLabel": "抄送",
      "printVisible": true,
      "type": "user",
      "required": false,
      "rules": [
        { "type": "pickerLimit", "max": 20, "message": "抄送人最多选择 20 人" }
      ],
      "maxLength": 0,
      "readonlyAfterSubmit": true
    },
    {
      "code": "attachments",
      "label": "附件",
      "printLabel": "附送材料",
      "printVisible": true,
      "type": "files",
      "required": false,
      "rules": [
        {
          "type": "filePolicy",
          "maxSizeMb": 50,
          "maxCount": 20,
          "allowExt": ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "heic", "wps", "zip", "rar", "7z"],
          "denyExt": ["exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr"],
          "message": "附件仅支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/wps/zip/rar/7z，单个文件不超过 50MB；heic 转 jpg 预览、wps 请下载查看"
        }
      ],
      "maxLength": 0,
      "readonlyAfterSubmit": false
    }
  ]
}
```

**示例说明（`involve_cost` 的双重联动）**：

| 环节 | 行为 |
| --- | --- |
| `involve_cost = false`（默认） | `amount` 与 `cost_bearer` 隐藏、非必填、值被清空；流程②被跳过 |
| `involve_cost = true` | `amount` 与 `cost_bearer` 显示且必填；②执行审批 |
| 提交后 | 全部主字段只读；仅 `attachments` 因补件场景 `readonlyAfterSubmit = false`（配合服务端「待补件」白名单） |
| 打印稿 | `involve_cost` 的 `printVisible = false` → 不占独立字段行，作为「资金审批内容」段落内容输出（`forms.md` 第 10 节口径） |

**其他三类单据的字段项**：直接照搬 `forms.md` 第 3–5 节的字段表，逐字段补齐 `printLabel` / `printVisible` / `readonlyAfterSubmit` 三键即可；不得在 `form_schema_json` 中引入 `forms.md` 未定义的字段。

### 2.5 服务端校验要求（不可省略）

| 要求 | 说明 |
| --- | --- |
| 二次校验 | 所有 `required` / `rules` 必须由**服务端**执行，前端校验仅为体验 |
| 状态白名单 | 服务端按「草稿 / 审批中 / 待补件」三态校验可写字段，**不能仅依赖前端置灰** |
| 金额禁止浮点 | 只接受字符串或定点数，禁止经浮点运算后落库 |
| 字段级权限 | 金额与收款账号按角色脱敏/隐藏（金额导出：**系统管理员与财务角色可导出**，非财务角色仅可见） |
| 未知键 | 服务端遇到未登记的字段 `code` 一律**拒绝**，不允许静默透传 |

---

## 3. 模板版本与快照规则

### 3.1 三层版本

| 层 | 字段 | 写入时机 | 语义 |
| --- | --- | --- | --- |
| 模板版本 | `flow_template.version` | 每次发布 +1 | 表单与流程**共用同一版本号**，同一次发布同时升版 |
| 表单快照版本 | `form_data.schema_version` | **提交时**写入 | 固化提交时的表单模板版本，防止模板变更影响在途单据 |
| 实例锁定版本 | `flow_instance.template_version` | **发起时**写入 | 在途实例的执行依据；不加外键级联更新 |

### 3.2 快照规则

| 编号 | 规则 |
| --- | --- |
| V-01 | **发布新版本不覆盖历史**：`flow_template` 按 `(code, version)` 唯一累积，旧版本保留、可查、可回滚（回滚 = 把旧版本重新发布为新版本，不修改历史行） |
| V-02 | **在途实例锁版本**：实例发起时锁定 `template_version`，其**剩余节点**全部按该版本执行 |
| V-03 | **提交时固化字段定义与值**：把 `form_schema_json` 的 `schema_version` 与 `fields_json` **一并**写入 `form_data` |
| V-04 | **审批人快照同期固化**：`flow_instance.approver_snapshot_json` 在发起时一次性解析；调岗、离职、组织调整**不影响在途单据** |
| V-05 | **模板停用不影响在途**：`flow_template.status` 改为 `archived` 只阻止**新实例**使用该版本 |
| V-06 | **重新提交重新解析**：驳回后重新提交，**重新解析审批人快照与流程版本**（按最新模板），已审过的节点不保留（旧快照在审计日志中保留） |
| V-07 | **节点配置冻结**：`flow_node_instance` 保存发起时冻结的 `decision_mode` / `pass_threshold` / `approver_ids_json`，模板后续修改不影响已开始的节点 |
| V-08 | **版本号单调递增**：不得跳号、不得复用；`schema_version` 必须等于对应 `flow_template.version` |

### 3.3 模板状态机

| 状态 | 中文名 | 允许的操作 | 是否可被新实例使用 |
| --- | --- | --- | --- |
| `draft` | 草稿 | 编辑节点与字段 | 否 |
| `published` | 已发布 | 只读；如需改配置必须发布新版本 | **是**（同一 `code` 下最多一个 `published` 版本） |
| `archived` | 已归档 | 只读 | 否（但**在途实例继续执行**） |

---

## 4. 模板变更流程

### 4.1 标准流程（六步，缺一不可）

| 步骤 | 责任方 | 动作 | 产出 |
| --- | --- | --- | --- |
| 1 | 业务方 + 产品 | 提出字段/节点变更需求，说明是否影响在途单据 | 变更单 |
| 2 | 产品 | **改字段 → 更新 `forms.md` 并升版本**；涉及枚举值时同步 `enums.md`；涉及字典时同步 `dict-seed.md` | 文档 PR |
| 3 | 开发 | 按新文档更新 `form_schema_json` 结构或节点配置代码 | 代码 PR |
| 4 | 流程管理员 | 管理后台**新增模板版本**（不修改历史版本），编辑节点与字段，保存为 `draft` | 新 `flow_template` 行 |
| 5 | 流程管理员 | 发布新版本（`status = published`，原 `published` 版本转 `archived`） | 新版本生效 |
| 6 | 验证 | 验证「**旧单据不受影响**」：在途单据剩余节点仍按原版本执行；新建单据按新版本执行 | 验收记录 |

### 4.2 变更类型与影响面

| 变更类型 | 是否升版本 | 在途实例影响 | 备注 |
| --- | --- | --- | --- |
| 新增字段 | **是** | 无（在途实例的 `fields_json` 不含新键，按空值渲染） | 新字段对旧单据不显示 |
| 修改字段标签 / `printLabel` / `printVisible` | **是** | 无 | 打印稿按模板渲染，旧单据仍按旧模板 |
| 修改字段校验（`rules`） | **是** | 无 | 提交时已按旧模板校验完成 |
| 删除字段 | **是** | 无 | **字段 `code` 不得复用**，删除后该 code 永久保留为废弃 |
| 新增/删除节点 | **是** | 无（在途实例锁版本） | 旧实例按旧节点集执行 |
| 修改节点超时 / 签名策略 | **是** | 无 | `flow_node_instance` 已冻结运行时配置 |
| 修改字典选项（增删选项） | **否**（字典改动无需发版） | 无 | 字段值为快照存储，历史单据显示原 code 的中文名 |
| 修改枚举值域 | **是**（代码发布） | 无 | 见 `enums.md` §0.1 |

### 4.3 禁止事项

| 禁止 | 原因 |
| --- | --- |
| 直接修改已 `published` 版本的 `form_schema_json` 或节点配置 | 会让在途单据的渲染与审批结果不可复现 |
| 复用已删除字段的 `code` | 会让历史数据含义漂移（`forms.md` 明确「字段 code 一经使用不得复用」） |
| 删除已产生的模板版本 | 在途实例会失去执行依据 |
| 修改已审批通过单据的字段值 | 内控红线；改主字段只能驳回重提 |

---

## 5. 打印规格对应关系

### 5.1 `printLabel` / `printVisible` 与 `DESIGN.md` 的对应

| 模板键 | `DESIGN.md` 依据 | 实现要求 |
| --- | --- | --- |
| 字段顺序（`fields[]` 顺序） | 打印规格「实现约束」第 3 条：字段顺序与标签取自表单模板 | 打印稿按 `fields` 数组顺序渲染，**不得**在打印模板中重排 |
| `printLabel` | `forms.md` 第 10 节「界面标签 → 打印标签对照（一期固定）」 | 打印稿使用 `printLabel`；映射表由 `forms.md` 维护，模板只落地 |
| `printVisible` | `forms.md` 第 10 节「打印可见性」（默认 true；内部备注类字段可设 false） | `false` = **不占独立字段行**，但可被版式固定段落引用（见 §5.2） |
| `sections[].printTitle` | 打印规格「子公司内部审批单的结构」中的**分组标题行**（整行合并居中） | 分组标题行取自 `printTitle`，缺省沿用 `title` |
| `type = boolean` / `multiselect` | 打印规格「结构要件」第 7 条：使用 `☑ / ☐`，不使用 `input[type=checkbox]` | 打印渲染器按 `type` 选择勾选框渲染，不得使用 HTML 复选框控件 |
| `type = amount` | 打印规格「打印稿专用规则」：等宽字体 + 千分位 + 两位小数 | 金额走等宽字体，`≥ 100 万` 时同时显示万元换算 |
| 节点 `sign_policy = required` | 打印规格「结构要件」第 4 条：多轮签名栏 | **签名栏由节点配置驱动**，不由字段驱动；强制签名节点在打印稿留空白签名栏「签名：____ 年 月 日」 |
| 打印稿样式隔离 | 打印规格「实现约束」第 1、2、5 条 | 打印稿使用 `mm` / `pt`，**不复用**业务界面组件类；打印稿与业务界面**共享数据、不共享样式** |
| 页脚 | 打印规格「页眉页脚与呈现」 | 左「系统名 · 单据名」、中「单号 · 模板版本 · 生成时间」、右「第 N 页 / 共 M 页」；**模板版本**取 `flow_instance.template_version` |

### 5.2 `printVisible = false` 的边界（易误用）

| 场景 | 正确做法 |
| --- | --- |
| 字段完全不打印（如纯内部备注） | `printVisible = false`，且版式模板不引用该字段 |
| 字段不单独成行、但内容合并进正文段落（如 `involve_cost` 进入「资金审批内容」） | `printVisible = false`，并在**版式模板的固定段落**中显式引用该字段值 |
| 字段需要显示 | `printVisible = true` |
| 隐藏整个版式行（如实单上的「其他会审部门」行） | 从 `form_schema_json.fields[]` 中删除该字段项，而非仅设 `printVisible = false` |

### 5.3 四类单据打印版式映射

**优先级规则**（自上而下，先命中先适用）：

1. **单据类型专属规则**：事项审批单 → **统一内部审批单版式**；印鉴证照审批单 → **印鉴证照版式**。
2. **层级规则**：集团层 → **集团版式**；子公司层 → **内部审批单版式**。

**层级判定**：取 `flow_instance.current_dept_id` 对应组织的 `org_type`——`group` 判为**集团层**；`company` / `dept` / `section` 判为**子公司层**。若 `current_dept_id` 为空（未上集团流转的单据），按发起人所属公司层级判定。

| 单据类型 | 判定层级 | 打印版式 | `DESIGN.md` 版式来源 | 样张编号 |
| --- | --- | --- | --- | --- |
| `matter` 事项审批单 | **全部层级（统一，忽略层级规则）** | **内部审批单版式** | 子公司内部审批单（竖向字段 + 审批记录流水） | P3 |
| `fund` 资金审批单 | 集团层 | **集团资金审批单版式** | 严格对齐实单 `doc/参考文档/集团资金审批单.png` | P2 |
| `fund` 资金审批单 | 子公司层 | 内部审批单版式 | 同上 | P3 |
| `contract` 合同审批单 | 集团层 | **集团合同类文件流转审批单版式** | 严格对齐实单 `doc/参考文档/集团合同审批流转单.png` | P1 |
| `contract` 合同审批单 | 子公司层 | 内部审批单版式 | 同上 | P3 |
| `seal` 印鉴证照审批单 | **全部层级（统一，忽略层级规则）** | **印鉴证照使用审批单版式** | 实单无对应件，沿用集团单版式推导 | P4 |

> **定稿口径（T-05，已关闭）**：`seal` 在**子公司层**时，「印鉴用印鉴证照版式」与「子公司层统一内部审批单版式」两条规则冲突。**V0.4 定稿按单据类型专属规则优先**（即仍用印鉴证照版式）；后续如业务调整，只改本行口径、不影响模板结构。

**版式结构要点（与模板字段的绑定）**：

| 版式 | 结构要点 | 模板绑定 |
| --- | --- | --- |
| P1 集团合同类文件流转审批单 | 「公文接收及处理」重复块（最多 3 段），每段对应一次集团层流转 | `flow_routing` 记录数；每段的「处理意见」取自该节点的 `sys_thread.opinion` |
| P1 | 合并栏「集团领导意见」（分管领导 + 董事长同格） | 节点 ⑤ 与 ⑥ 的轨迹合并渲染，中间留 5–6mm |
| P1 | 收尾行「公文回传」+「印鉴证照管理部门」 | 节点 ⑦ 的归档登记信息 |
| P2 集团资金审批单 | 多轮签名栏「集团职能部门 / 集团分管领导 / 集团董事长」 | 节点 ② ⑤ ⑥ 的 `sign_policy` 与签名图 |
| P2 | 收尾行「系统关联」（关联单号 + 审批链） | `flow_instance.biz_no` + 主干 7 节点链路 |
| P3 子公司内部审批单 | 顶部「申请编号」+「打印人 / 打印时间」 | `form_data.biz_no` + 当前登录用户与打印时间 |
| P3 | 竖向字段表（申请人 / 申请时间 / 所属部门 / 审批状态） | 发起人快照字段 + `flow_instance.status` |
| P3 | 审批记录流水（阶段 / 处理人 + 动作 + 时间 / 意见与附件） | `sys_thread` 顺序记录；**撤回与重审各自独立一行**，不做合并 |
| P4 印鉴证照使用审批单 | 保留实单标签「集团职能部门」，不改为「财务部」 | 打印标签固定为实单写法（`templates.md` §5.1） |

### 5.4 打印实现约束（落地清单）

| 编号 | 约束 |
| --- | --- |
| P-01 | 打印稿**不使用任何主题色**，全部黑色；状态以文字呈现，不得依赖颜色传递信息 |
| P-02 | 打印稿圆角全部 `0px`，全表 1pt 实线，禁止投影与渐变 |
| P-03 | 纸张 A4 纵向 210mm × 297mm，版心 186mm，页码「第 N 页 / 共 M 页」 |
| P-04 | 一张单据固定一页；内容超出按行分页并重复表头（`thead`） |
| P-05 | 已签署的电子签名打印为**缩略图 + 时间戳文字**；未签署的强制签名节点打印为**空白签名栏** |
| P-06 | 附件清单区只列文件名（不含文件本体），补件附件标注轮次（`round`） |
| P-07 | 打印稿与业务界面**共享数据、不共享样式**；不得复用 `.btn` / `.card` / `.pill` 等界面类 |
| P-08 | 打印稿的字段顺序、标签、可见性**完全由模板驱动**，不得在打印模板中硬编码字段 |

---

## 6. 变更记录

| 版本 | 日期 | 修改说明 |
| --- | --- | --- |
| V0.4 | 2026-07-09 | 首版：给出四类单据 × 7 节点共 28 行节点配置表与事项单 `flow_node` 种子 JSON；定义 `form_schema_json` 顶层与字段项结构契约（含 `rules[]` 规则类型表）与事项单最小可用完整示例；明确三层版本与 8 条快照规则；给出六步模板变更流程与 4 类禁止事项；建立 `printLabel` / `printVisible` 与 `DESIGN.md` 打印规格的对应关系及四类单据版式映射 |
| V0.4（业务裁定后修订） | 2026-07-09 | §0 待业务确认项**全部关闭**，改为「定稿默认值」表（T-01–T-08）并新增 §0.1 技术执行项；超时定稿 **② = 48h、其余节点 = 24h**（四类模板与 `flow_node` 种子 JSON 同步）；⑦ `archive_register` 明确**默认「仅登记不审批」**：`decision_mode` / `pass_threshold` 置 `null`、不产生审批决议、不计入审批时长与效率统计、仅留痕 `sys_thread.action = archive_register`；协同任务**每个协同部门一条站内信**；附件 `allowExt` 13 → **15 种**（放行 `heic` / `wps` + 预览降级）；字段 code 统一为 `other_review_depts`（字典类型仍为 `review_dept_other`）；`allow_route` 仅 ②⑤⑥、`allow_jump` 全关闭标为定稿 |

---

> **交叉引用**：枚举取值见 [`enums.md`](enums.md)；字典种子数据见 [`dict-seed.md`](dict-seed.md)；表结构以 [`data-model.md`](data-model.md) 为准；字段业务含义与校验文案以 [`forms.md`](forms.md) 为准；打印视觉规格与样张见 [`DESIGN.md`](../DESIGN.md) 与 [`DESIGN.print-a4.html`](../DESIGN.print-a4.html)。
