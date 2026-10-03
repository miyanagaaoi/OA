# 集团OA审批系统 · 数据字典种子数据（dict-seed.md）

| 项目 | 内容 |
| --- | --- |
| 文档用途 | 给出 `sys_dict_item` 的**可直接初始化**的种子数据，并明确「哪些值走字典、哪些值走代码枚举、哪些值既不是枚举也不是字典」 |
| 权威边界 | **取值 code** 一律以 [`enums.md`](enums.md) 为准；**表结构**以 [`data-model.md`](data-model.md) §3.6 为准；**字段与字典的绑定关系**以 [`forms.md`](forms.md) 为准。本文档只负责**种子数据内容与初始化方式** |
| 与 PRD 的关系 | 对应 REQ-ADMIN-004（数据字典由管理后台维护，新增选项不需发版） |
| 与 templates.md 的关系 | `form_schema_json.fields[].optionsSource` 引用的 `dictType` 必须存在于本文档；见 [`templates.md`](templates.md) §2 |
| 版本 | **V0.4 配套** · 状态：评审稿 · 基准日期：2026-07-09 |
| 初始化时机 | 数据库建表后、跑通 REQ-FLOW-012（发起前拦截）之前；**四类单据模板发布前必须完成** |

---

## 0. 初始化须知

### 0.1 初始化方式（二选一）

| 方式 | 适用 | 说明 |
| --- | --- | --- |
| **A. SQL INSERT（本文档默认）** | 实施工程师首次部署 | 各表顶部给出可直接执行的 `INSERT`；**幂等**写法为 `INSERT ... ON DUPLICATE KEY UPDATE`，重复执行只更新名称/排序/启用态，不产生重复行 |
| **B. 管理后台导入模板** | 业务管理员后续维护 | 导入文件为 CSV/Excel，列顺序：`dict_type, item_code, item_name, item_name_en, sort_no, status, remark`；首行为表头；导入前必须校验 `dict_type` 属于本文档第 8 节的**白名单**，拒绝未登记的类型 |

### 0.2 表结构前置说明（**已收口：两列由 `data-model.md` 的建表负责，本文档不输出 ALTER**）

`data-model.md` 3.6 的 `sys_dict_item` **已包含** `item_name_en`（英文名）与 `remark`（备注）两列（列注释写明其承载本文档的 V0.4 种子数据）。

因此**本文档只输出幂等 `INSERT`，不得输出任何 `ALTER TABLE`**。历史教训（2026-10-02 实测）：本节曾给出过一段"建议补列"的 `ALTER TABLE ... ADD COLUMN item_name_en / remark`；当 `data-model.md` 把这两列补进建表语句后，该 ALTER 变成**冗余且致命**——Flyway 在**全新库**上执行 `V1（建表已含两列）→ V2（再次 ADD COLUMN）` 会直接报 `SQL State 42S21 / Error 1060 Duplicate column name 'item_name_en'`，导致**应用无法启动**（`flyway_schema_history` 只会留下 V1 success=1 + V2 success=0）。已从源头修正，并由 `tools/gen-init-sql.js` 增加**漂移断言**防回归：建表语句若已含该列，种子中再出现 `ADD COLUMN <同名列>` 即判为 error。

> 若业务确认**不需要**英文名与备注，应在 `data-model.md` 的建表语句中删除这两列（而不是在种子脚本里加回 ALTER）；本文档表格中的「英文」「备注」列仅作知识记录保留。


### 0.3 字典类型白名单（对应 §8 边界表）

`matter_category` · `contract_type` · `seal_type` · `cert_type` · `payment_method` · `return_status` · `group_dept` · `review_dept_other`

> `payment_belong` 与 `plan_category` **不在白名单内**——它们是 **checkbox 布尔字段**（`type: "boolean"` + `defaultValue: true`），**既不入 `sys_dict_item`、也不写入 `form_schema_json.options`**（见 §6、§7）。

### 0.4 业务裁定结论（原「待业务确认项」，**全部已关闭**）

> 本节所有条目已由业务方裁定，种子数据已按结论定稿。**业务侧无未决项**；`data-model.md` / `forms.md` 的回写差异见 `enums.md` §0.3。

| 编号 | 字典 | 原待确认内容 | 定稿结论（已落地） | 状态 |
| --- | --- | --- | --- | --- |
| D-01 | `contract_type` | 是否把「购销」拆回「采购 / 销售」 | **不拆**：保留单一 `purchase` = **购销**；`sales` **不纳入**（§2） | **已关闭** |
| D-02 | `contract_type` | 是否保留独立的「劳务合同」 | **保留** `labor` = 劳务（§2） | **已关闭** |
| D-03 | `seal_type` | 「证照章」与「证照借用」是否并存 | **并存**：`cert_seal` 与 `cert_borrow` 均保留（§3） | **已关闭** |
| D-04 | `cert_type` | 是否新增「开户许可证」 | **不新增**：`bank_account_license` **已从种子数据移除**，证照类型定稿 5 值（§4） | **已关闭** |
| D-05 | `payment_method` | 「电汇」还是「银行转账」 | **保持现状**：中文名用 **「银行转账」**（按现有纸质实单用词保留），**不改名「电汇」**（§5） | **已关闭** |
| D-06 | `payment_method` | 是否新增「委托付款」 | **不新增**：`entrust` **已从种子数据移除**，付款方式定稿 4 值（§5） | **已关闭** |
| D-07 | `payment_belong` / `plan_category` | 是否需要在数据库侧留痕 | **不入 `sys_dict_item`**（维持原结论）；§11 自检**保留**「不得出现 `payment_belong` / `plan_category` 行」的校验（§6、§7） | **已关闭** |
| D-08 | `group_dept` | 集团职能部门是否持久化为字典 | **保留为字典**，但**仅是「集团职能部门」标签字典**（用于打印与统计）：**组织关系一律以 `sys_org` 为准，禁止双写组织结构**（§8.1） | **已关闭** |
| D-09 | `review_dept_other` | 是否直接由 `group_dept` 驱动 | **两个字典都保留**；`review_dept_other` 的选项**来源于 `group_dept`**，二者**同源同 code**；字段 code 为 `other_review_depts`（§8.2） | **已关闭** |
| D-10 | `return_status` | 「无需归还」是否为独立状态 | **保留三值**（未归还 / 已归还 / 无需归还），并把「无需归还」的实现口径写为**终态备注**：**不参与超期提醒与催办**（§9） | **已关闭** |

**标记约定**：种子数据中标注「已按业务确认」的行即为定稿值；字典值均为快照存储，后续调整不影响在途单据。

### 0.5 字段 code 命名定稿（**与 `forms.md` 对齐**）

| 项 | 定稿 code | 说明 |
| --- | --- | --- |
| 计划类别**字段** | `plan_category` | 旧写法 `planned_category` **作废**；非字典项 |
| 付款归属**字段** | `payment_belong` | 非字典项 |
| 其他会审部门**字段** | `other_review_depts` | `multiselect`；旧写法把字段写成 `review_dept_other` **作废** |
| 其他会审部门**字典类型** | `review_dept_other` | 字典类型名**保持不变** |

> 口径：**字段 code 与字典类型 code 是两个命名空间**。`other_review_depts` 是字段，`review_dept_other` 是字典类型；不得互相替代。

---

## 1. `matter_category` 事项类别（5 项）

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('matter_category', 'business', '经营', 'Business',    10, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'economy',  '经济', 'Economy',     20, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'admin',    '行政', 'Admin',       30, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'hr',       '人力', 'HR',          40, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'invest',   '投资', 'Investment',  50, 'active', 'Q10 新增；集团归口恒为财务部；不参与路由')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `matter_category` | `business` | 经营 | Business | 10 | 是 | 集团归口恒为财务部；**不参与路由**；`forms.md` 6.1 旧码为 `operate`（见 `enums.md` §14） |
| `matter_category` | `economy` | 经济 | Economy | 20 | 是 | 资金单默认类别 |
| `matter_category` | `admin` | 行政 | Admin | 30 | 是 | 印鉴证照单默认类别 |
| `matter_category` | `hr` | 人力 | HR | 40 | 是 | — |
| `matter_category` | `invest` | 投资 | Investment | 50 | 是 | Q10 新增；与其他四类走**完全相同**的流程，无特殊节点 |

**使用位置**：`form_schema_json.fields[code=category].optionsSource.dictType = "matter_category"`；四类单据的 `category` 字段共用本字典。

**约束**：类别选定后**任何节点不可改判**；字典**新增**类别后表单即刻可选（PRD AC-03）；字典**删除**类别不影响已发起单据（值为快照存储）。

---

## 2. `contract_type` 合同类型（6 项）

> **与合同管理系统集成的对齐说明（D-01 备注）**：合同类型后续与 **PRD 8.2 合同管理系统集成**对齐，二期接入时**以合同系统台账类型为准**；本表为一期基线。

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('contract_type', 'purchase',     '购销',   'Purchase and Sale', 10, 'active', 'D-01 已按业务确认：不拆「采购/销售」，保留单一「购销」；二期与 PRD 8.2 合同系统台账类型对齐'),
('contract_type', 'service',      '服务',   'Service',           30, 'active', ''),
('contract_type', 'lease',        '租赁',   'Lease',             40, 'active', ''),
('contract_type', 'construction', '工程',   'Construction',      50, 'active', ''),
('contract_type', 'labor',        '劳务',   'Labor Service',     60, 'active', 'D-02 已按业务确认：保留独立劳务类型'),
('contract_type', 'other',        '其他',   'Other',            999, 'active', '选择本项时 contract_type_other 必填')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `contract_type` | `purchase` | 购销 | Purchase and Sale | 10 | 是 | **已按业务确认**：**不拆**「采购 / 销售」，保留单一「购销」；后续与 **PRD 8.2 合同管理系统集成**对齐，二期接入时**以合同系统台账类型为准** |
| `contract_type` | `service` | 服务 | Service | 30 | 是 | — |
| `contract_type` | `lease` | 租赁 | Lease | 40 | 是 | — |
| `contract_type` | `construction` | 工程 | Construction | 50 | 是 | — |
| `contract_type` | `labor` | 劳务 | Labor Service | 60 | 是 | **已按业务确认**：保留独立劳务类型 |
| `contract_type` | `other` | 其他 | Other | 999 | 是 | 选此项时 `contract_type_other`（文本，≤40）条件必填 |

> **不纳入的 code**：`sales`（销售）**不入种子数据**——D-01 已裁定不拆分「购销」。`forms.md` 6.3 现仍为「采购合同 / 销售合同」拆分口径，需主控回写（见 `enums.md` §0.3 S-15）。若二期确需拆分，新增 code 并升版本，**不得复用 `sales`**。

**使用位置**：合同审批单 `contract_type` 字段；打印标签见 [`templates.md`](templates.md) §5。

---

## 3. `seal_type` 用印类型（6 项）

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('seal_type', 'company_seal', '公章',     'Company Seal',              10, 'active', '需填用印份数'),
('seal_type', 'contract_seal','合同章',   'Contract Seal',             20, 'active', '合同单拟用印类型默认值'),
('seal_type', 'finance_seal', '财务章',   'Finance Seal',              30, 'active', '需填用印份数'),
('seal_type', 'legal_seal',   '法人章',   'Legal Representative Seal', 40, 'active', '需填用印份数'),
('seal_type', 'cert_seal',    '证照章',   'Certificate Seal',          50, 'active', 'D-03 已按业务确认：与 cert_borrow 并存'),
('seal_type', 'cert_borrow',  '证照借用', 'Certificate Borrow',        60, 'active', '选择本项时必填 cert_type，且隐藏 seal_count')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `seal_type` | `company_seal` | 公章 | Company Seal | 10 | 是 | 需填用印份数 |
| `seal_type` | `contract_seal` | 合同章 | Contract Seal | 20 | 是 | 合同单 `sign_seal_type` 默认值 |
| `seal_type` | `finance_seal` | 财务章 | Finance Seal | 30 | 是 | 需填用印份数 |
| `seal_type` | `legal_seal` | 法人章 | Legal Representative Seal | 40 | 是 | 需填用印份数 |
| `seal_type` | `cert_seal` | 证照章 | Certificate Seal | 50 | 是 | **已按业务确认（D-03）**：与「证照借用」**并存** |
| `seal_type` | `cert_borrow` | 证照借用 | Certificate Borrow | 60 | 是 | 选中时 `cert_type` 必填、`seal_count` 隐藏 |

> **`forms.md` 6.4 现仅列 5 值（无 `cert_seal`）**——D-03 裁定二者并存，`cert_seal`（证照章）需由主控回写 `forms.md`（见 `enums.md` §0.3 S-15）。

**联动规则（写入 `form_schema_json.fields[code=cert_name].linkage`）**：`seal_type = cert_borrow` → 字段 **`cert_name`**（标签「证照类型」，字典类型 `cert_type`）显示且必填；`seal_type ≠ cert_borrow` → `seal_count` 显示且必填（1–999 整数）。

> **命名辨析**：字段 code 为 **`cert_name`**（`forms.md` 5 沿用，不复用改名），**字典类型**为 **`cert_type`**——两者是不同命名空间，勿混用。

---

## 4. `cert_type` 证照类型（5 项）

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('cert_type', 'business_license',     '营业执照',     'Business License',                10, 'active', ''),
('cert_type', 'tax_cert',             '税务登记证',   'Tax Registration Certificate',    20, 'active', ''),
('cert_type', 'org_code',             '组织机构代码证','Organization Code Certificate',  30, 'active', ''),
('cert_type', 'qualification',        '资质证书',     'Qualification Certificate',       40, 'active', ''),
('cert_type', 'other',                '其他',         'Other',                          999, 'active', '')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `cert_type` | `business_license` | 营业执照 | Business License | 10 | 是 | — |
| `cert_type` | `tax_cert` | 税务登记证 | Tax Registration Certificate | 20 | 是 | — |
| `cert_type` | `org_code` | 组织机构代码证 | Organization Code Certificate | 30 | 是 | — |
| `cert_type` | `qualification` | 资质证书 | Qualification Certificate | 40 | 是 | — |
| `cert_type` | `other` | 其他 | Other | 999 | 是 | — |

> **不纳入的 code（D-04 已关闭）**：`bank_account_license`（开户许可证）**已按业务确认不纳入**，已从种子数据移除。若后续确有需要，新增 code 并升版本，**不得复用**。

**命名对齐**：`data-model.md` 3.6 的旧 `dict_type` 为 `cert_name`，`forms.md` 6.5 的标题为「证照名称」；V0.4 统一为 `cert_type`（`enums.md` §14）。

---

## 5. `payment_method` 付款方式（4 项）

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('payment_method', 'transfer',   '银行转账', 'Bank Transfer',      10, 'active', 'D-05 已按业务确认：按现有纸质实单用词保留「银行转账」，不改名「电汇」'),
('payment_method', 'acceptance', '银行承兑汇票','Bank Acceptance',  20, 'active', ''),
('payment_method', 'cash',       '现金',     'Cash',               30, 'active', ''),
('payment_method', 'other',      '其他',     'Other',             999, 'active', '')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `payment_method` | `transfer` | 银行转账 | Bank Transfer | 10 | 是 | **已按业务确认（D-05）**：**按现有纸质实单用词保留「银行转账」**，不改名「电汇」 |
| `payment_method` | `acceptance` | 银行承兑汇票 | Bank Acceptance | 20 | 是 | 与纸质实单用词一致 |
| `payment_method` | `cash` | 现金 | Cash | 30 | 是 | — |
| `payment_method` | `other` | 其他 | Other | 999 | 是 | — |

> **不纳入的 code（D-06 已关闭）**：`entrust`（委托付款）**已按业务确认不新增**，已从种子数据移除。本表与 `forms.md` 6.2 完全一致（4 值）。

**命名对齐**：`data-model.md` 3.6 的旧 `dict_type` 为 `pay_method`，V0.4 统一为 `payment_method`。

---

## 6. `payment_belong` 付款归属（**checkbox，不是字典项**）

> **明确结论**：`payment_belong` 与 `plan_category` 在 V0.4 定稿中均为 **checkbox 布尔字段**（默认均为勾选），**不是字典项**，因此：
>
> 1. **不写入 `sys_dict_item`**，不执行任何 `INSERT`；
> 2. **不写 `options`**：布尔字段用 `type: "boolean"` + `defaultValue: true` 表达（`templates.md` §2.2 已明确 `options` 仅用于 `select` / `multiselect`）；
> 3. 一期**只存不用**——流程引擎、数据域过滤、超时规则一律不得引用该字段（PRD 6.1、`data-model.md` 4.4）。

**勾选状态说明（仅用于界面与打印渲染，不得据此建字典项）**：

| 字段 | 选项 code | 中文 | 英文 | 排序 | 默认勾选 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `payment_belong` | `current_month` | 本月度 | Current Month | 10 | **是** | 打印稿呈现为「（本月度）月 / 本年度 / 以前年度」 |
| `payment_belong` | `current_year` | 本年度 | Current Year | 20 | 否 | — |
| `payment_belong` | `prior_year` | 以前年度 | Prior Year | 30 | 否 | — |

**单选语义说明（D-07 已关闭）**：三个选项在业务上是**三选一**，V0.4 定稿即以 checkbox 布尔字段实现（默认勾选「本月度」），**不入 `sys_dict_item`**；§11 自检保留「不得出现 `payment_belong` / `plan_category` 行」的强制校验。若二期确需严格互斥单选，再改为 `select` 型并把选项迁入 `sys_dict_item`（`dict_type = payment_belong`），届时同步更新 `enums.md` §1.3。

**打印标签**：`printLabel = "付款归属"`；按 `DESIGN.md`「打印规格」第 7 条以 `☑ / ☐` 呈现，不使用 `input[type=checkbox]`。

---

## 7. `plan_category` 计划类别（**checkbox，不是字典项**）

> **明确结论**：同 §6。**字段 code 统一为 `plan_category`**（旧写法 `planned_category` 作废）；`plan_category` 是 **checkbox 布尔字段**，**不是字典项**，不写入 `sys_dict_item`。

**选项清单（存于 `form_schema_json`）**：

| 字段 | 选项 code | 中文 | 英文 | 排序 | 默认勾选 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `plan_category` | `in_plan` | 计划内 | In Plan | 10 | **是** | 一期仅存储 |
| `plan_category` | `out_plan` | 计划外 | Out of Plan | 20 | 否 | 一期仅存储 |

**一期边界（避免误解）**：

- **不做**资金计划模块：没有计划编制、没有计划与实际的比对、没有超计划拦截。
- `plan_category` **不得**被流程条件（`flow_node.skip_condition`）、数据域过滤或超时规则引用。
- 二期「计划管理」模块上线后，再评估是否提升为独立数据库列并加索引（届时需对历史数据回填）。

---

## 8. `group_dept` 集团职能部门 与 `review_dept_other` 其他会审部门

> **本节两个字典的分工（D-08 / D-09 已关闭）**：`group_dept` 是**「集团职能部门」标签字典**（仅用于打印与统计）；`review_dept_other` 的选项**来源于 `group_dept`**，二者**同源、同 code**。字段 code 为 `other_review_depts`，字典类型为 `review_dept_other`（命名空间不同，见 §0.5）。

### 8.1 `group_dept` 集团职能部门（4 项，**标签字典**）

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('group_dept', 'econ_dev',     '经发部',       'Economic Development Dept', 10, 'active', '标签字典；审批归口已统一为财务部，本部门仅作协同/会审'),
('group_dept', 'finance',      '财务部',       'Finance Dept',              20, 'active', '标签字典；集团归口部门（恒为财务部）'),
('group_dept', 'hr_dept',      '人力资源部',   'HR Dept',                   30, 'active', '标签字典；仅作协同/会审'),
('group_dept', 'group_office', '集团办',       'Group Office',              40, 'active', '标签字典；仅作协同/会审')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `group_dept` | `econ_dev` | 经发部 | Economic Development Dept | 10 | 是 | 归口统一为财务部后，本部门仅作**协同/会审** |
| `group_dept` | `finance` | 财务部 | Finance Dept | 20 | 是 | **集团归口部门恒为财务部** |
| `group_dept` | `hr_dept` | 人力资源部 | HR Dept | 30 | 是 | 仅作协同/会审 |
| `group_dept` | `group_office` | 集团办 | Group Office | 40 | 是 | 仅作协同/会审 |

> **定位与硬约束（D-08 定稿）**：`group_dept` **仅是「集团职能部门」标签字典**，用途限定为**打印与统计**（打印稿标签、报表分组）。**组织关系一律以 `sys_org` 为准，禁止双写组织结构**：
>
> 1. **禁止**用本字典驱动组织树、数据域过滤、审批人解析——这些一律查 `sys_org`；
> 2. **禁止**在 `sys_org` 与本字典之间做双向同步；本字典**不得**承载 `parent_id`、`leader_id`、`path` 等组织字段；
> 3. 若某部门在本字典与 `sys_org` 中不一致，**以 `sys_org` 为准**，本字典仅在打印/统计场景使用其 `item_name`；
> 4. 新增/停用集团职能部门时，须**同时在 `sys_org` 与后台字典中维护**（字典改动不需发版），并写入权限变更日志。

### 8.2 `review_dept_other` 其他会审部门（**字典类型**；合同单字段 `other_review_depts` 的来源）

**背景**：`DESIGN.md`「业务确认后的两处版式口径」引入——纸质实单上「其他会审部门」一行与「事项分类」语义相近但**允许多选**，系统侧映射为**数据字典驱动的多选字段**（字段 code `other_review_depts`），标签保留实单写法「其他会审部门」。

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('review_dept_other', 'econ_dev',     '经发部',     'Economic Development Dept', 10, 'active', 'D-09 已按业务确认：与 group_dept.econ_dev 同源同 code'),
('review_dept_other', 'finance',      '财务部',     'Finance Dept',              20, 'active', 'D-09 已按业务确认：与 group_dept.finance 同源同 code'),
('review_dept_other', 'hr_dept',      '人力资源部', 'HR Dept',                   30, 'active', 'D-09 已按业务确认：与 group_dept.hr_dept 同源同 code'),
('review_dept_other', 'group_office', '集团办',     'Group Office',              40, 'active', 'D-09 已按业务确认：与 group_dept.group_office 同源同 code')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `review_dept_other` | `econ_dev` | 经发部 | Economic Development Dept | 10 | 是 | 与 `group_dept.econ_dev` **同源同 code** |
| `review_dept_other` | `finance` | 财务部 | Finance Dept | 20 | 是 | 与 `group_dept.finance` 同源同 code |
| `review_dept_other` | `hr_dept` | 人力资源部 | HR Dept | 30 | 是 | 与 `group_dept.hr_dept` 同源同 code |
| `review_dept_other` | `group_office` | 集团办 | Group Office | 40 | 是 | 与 `group_dept.group_office` 同源同 code |

**字段绑定**：合同审批单 `other_review_depts`（`multiselect`，`optionsSource.dictType = "review_dept_other"`），`printLabel = "其他会审部门"`，`printVisible = true`。

**同源维护规则（D-09 定稿）**：

| 规则 | 说明 |
| --- | --- |
| 选项来源 | `review_dept_other` 的候选值**来源于 `group_dept`**，`item_code` 与中文名**逐一对应**；不得出现只在其中一个字典里存在的 code |
| 维护动作 | 在 `group_dept` 新增/停用部门时，**同步**在 `review_dept_other` 做同样的增删（同一 `item_code`） |
| 允许扩展 | 若确需「集团职能部门之外」的会审单位（如外部机构），**只在 `review_dept_other` 增补** code，`group_dept` 保持 4 项不变；此类扩展项须在备注中标注「非集团职能部门」 |
| 字段可隐藏 | 「若不保留该字段，此行为可配置隐藏」（`DESIGN.md` 口径）——通过从 `form_schema_json.fields[]` 删除 `other_review_depts` 字段项实现，**无需改代码**（T-06 已定稿：一期**保留**该字段） |

---

## 9. `return_status` 归还状态（3 项）

**初始化方式 A（SQL）**：

```sql
INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('return_status', 'pending',      '未归还',   'Not Returned', 10, 'active', '默认值'),
('return_status', 'returned',     '已归还',   'Returned',     20, 'active', '选择本项时 return_date 必填'),
('return_status', 'not_required', '无需归还', 'Not Required', 30, 'active', 'D-10 已按业务确认：保留三值；本项为终态备注，不参与超期提醒与催办')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
```

| dict_type | item_code | 中文 | 英文 | 排序 | 启用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `return_status` | `pending` | 未归还 | Not Returned | 10 | 是 | 默认值 |
| `return_status` | `returned` | 已归还 | Returned | 20 | 是 | 选中时 `return_date` 条件必填 |
| `return_status` | `not_required` | 无需归还 | Not Required | 30 | 是 | **已按业务确认（D-10）**：保留三值；本项为**终态备注**——**不参与超期提醒与催办** |

> **「无需归还」的实现口径（D-10 定稿）**：`not_required` **不改变字典值域**（仍为三值），但状态语义为**终态备注**：
>
> 1. **不参与超期提醒**——不进入「未归还超期」扫描（`flow_supplement` / 催办定时任务的扫描条件中排除本值）；
> 2. **不产生催办**——不发站内信、不发邮件、不抄送上级；
> 3. **可逆向修改**——若后续实际需要归还，允许把状态从 `not_required` 改回 `pending` / `returned`（仍受「审批中唯一可改主字段」白名单保护）；
> 4. 报表中「待归还」口径 = `pending`；`returned` 与 `not_required` 均不计入未归还数。

> **三态例外**：印鉴证照单的 `return_status` / `return_date` 是**唯一在审批中可改的主字段**，且**发起人与节点⑦（归档登记）均可修改**（V0.4 定稿；`forms.md` 5 原为「仅归档节点可改」，见 `enums.md` 0.3 S-13）。其余主字段审批中一律只读。

---

## 10. 枚举与字典边界说明（**避免两处定义**）

下表是「这个值放哪儿」的**唯一裁决表**。任何文档与实现出现分歧时，以本表 + [`enums.md`](enums.md) §1.2 为准。

| 值域 | 载体 | 存放位置 | 后台可增删 | 定义处 |
| --- | --- | --- | --- | --- |
| 流程节点码（7 个）、审批人解析规则码（9 个） | **枚举** | 代码常量 | 否 | `enums.md` §2、§3 |
| 实例状态 / 子状态 / 节点实例状态 / 任务状态 | **枚举** | 代码常量 + `CHECK` | 否 | `enums.md` §4–§6 |
| 流转动作 `route / rollback / back_home` | **枚举** | 代码常量 + `CHECK` | 否 | `enums.md` §7 |
| 消息类型（8 个） | **枚举** | 代码常量 | 否 | `enums.md` §8 |
| 审批轨迹动作（13 + 4 个） | **枚举** | 代码常量 | 否 | `enums.md` §9 |
| 单据类型 `matter / fund / contract / seal` | **枚举** | 代码常量 | 否 | `enums.md` §10.2 |
| 字段类型（14 种） | **枚举** | 代码常量 | 否 | `enums.md` §11 |
| 日志类别（5 类） | **枚举** | 代码常量 | 否 | `enums.md` §13 |
| **`sign_policy`**（`required / optional / none`） | **枚举** | `flow_node.sign_policy` + `CHECK` | 否 | `enums.md` §1.3；模板配置见 `templates.md` §1 |
| **`decision_mode`**（`any / all / sequential`） | **枚举** | `flow_node.decision_mode` + `CHECK` | 否 | `enums.md` §1.3；模板配置见 `templates.md` §1 |
| **`log_type`**（`operation / approval_thread / signature / permission_change / login`） | **枚举** | 代码常量（日志分类不落库为字段） | 否 | `enums.md` §13 |
| `matter_category` 事项类别 | **字典** | `sys_dict_item` | **是** | 本文档 §1 |
| `contract_type` 合同类型 | **字典** | `sys_dict_item` | **是** | 本文档 §2 |
| `seal_type` 用印类型 | **字典** | `sys_dict_item` | **是** | 本文档 §3 |
| `cert_type` 证照类型 | **字典** | `sys_dict_item` | **是** | 本文档 §4 |
| `payment_method` 付款方式 | **字典** | `sys_dict_item` | **是** | 本文档 §5 |
| `return_status` 归还状态 | **字典** | `sys_dict_item` | **是** | 本文档 §9 |
| `group_dept` 集团职能部门 | **字典**（**标签字典，仅用于打印与统计**） | `sys_dict_item`（**组织关系以 `sys_org` 为准，禁止双写**） | **是** | 本文档 §8.1 |
| `review_dept_other` 其他会审部门（字典类型） | **字典**（选项**来源于 `group_dept`**，同源同 code） | `sys_dict_item` | **是** | 本文档 §8.2 |
| **`payment_belong` 付款归属（字段）** | **checkbox 布尔字段（非字典）** | `form_schema_json.fields[].type = "boolean"` + `defaultValue: true` + `fields_json` | 否（改语义走模板升版本） | 本文档 §6 |
| **`plan_category` 计划类别（字段）** | **checkbox 布尔字段（非字典）** | `form_schema_json.fields[].type = "boolean"` + `defaultValue: true` + `fields_json` | 否（改语义走模板升版本） | 本文档 §7 |
| `other_review_depts` 其他会审部门（**字段**） | **字典驱动字段**（`multiselect`） | `form_schema_json.fields[]`，`optionsSource.dictType = "review_dept_other"` | 选项由字典维护；**字段本身**增删走模板升版本 | 本文档 §0.5、§8.2 |

**三条硬约束**：

1. **同一值域只允许一处定义**。例如「合同类型」只存在 `sys_dict_item`，代码中不得再出现 `ContractType` 枚举常量表。
2. **枚举值不得下沉为字典**。例如 `decision_mode` 若做成字典，管理员可新增「三分之二多数」这类值，而引擎不认识 → 会产生静默失效。
3. **字典值不得上升为枚举**。例如事项类别若做成枚举，新增类别必须发版，与 REQ-ADMIN-004、AC-03 冲突。

---

## 11. 初始化执行顺序与自检

| 步骤 | 动作 | 自检点 |
| --- | --- | --- |
| 1 | 执行 §0.2 的 `ALTER TABLE sys_dict_item` | `SHOW COLUMNS FROM sys_dict_item` 含 `item_name_en` 与 `remark` |
| 2 | 按 §1 → §9 顺序执行 `INSERT`（幂等） | `SELECT dict_type, COUNT(*) FROM sys_dict_item GROUP BY dict_type` 行数符合下方预期 |
| 3 | 确认 `payment_belong` / `plan_category` **未入库**（D-07 强制校验） | `SELECT * FROM sys_dict_item WHERE dict_type IN ('payment_belong','plan_category','planned_category') OR item_code IN ('in_plan','out_plan','current_month','current_year','prior_year')` 返回 **0 行** |
| 3.1 | 确认两个 checkbox 字段的值**只**出现在 `fields_json` | `SELECT COUNT(*) FROM form_data WHERE JSON_EXTRACT(fields_json,'$.plan_category') IS NOT NULL` 正常；`sys_dict_item` 中查无对应条目 |
| 4 | 校验字典类型白名单 | 无 `dict_type` 属于 §0.3 白名单之外的值（除历史遗留 `category` / `pay_method` / `cert_name`，需按 `enums.md` §14 迁移后消失） |
| 4.1 | 校验 `group_dept` 未被当作组织源使用（D-08） | 代码/配置中**无**按 `dict_type='group_dept'` 查询组织树、数据域或审批人的语句 |
| 4.2 | 校验 `review_dept_other` 与 `group_dept` 同源（D-09） | 两表 `item_code` 集合一致（`group_dept` 允许被 `review_dept_other` 扩展，反向不允许）：`SELECT item_code FROM sys_dict_item WHERE dict_type='review_dept_other' AND item_code NOT IN (SELECT item_code FROM sys_dict_item WHERE dict_type='group_dept')` 结果中的行，备注必须含「非集团职能部门」 |
| 5 | 校验 code 命名合规 | 全部 `item_code` 匹配 `^[a-z][a-z0-9_]{1,31}$` |
| 5.1 | 校验不纳入的 code 未出现 | `SELECT * FROM sys_dict_item WHERE (dict_type='contract_type' AND item_code='sales') OR (dict_type='cert_type' AND item_code='bank_account_license') OR (dict_type='payment_method' AND item_code='entrust')` 返回 0 行 |
| 6 | 校验中文名非空 | 全部 `item_name` 非空且无前后空格 |
| 7 | 发布四类单据流程模板 | 见 [`templates.md`](templates.md) §1、§3 |

**预期行数**：`matter_category` 5 + `contract_type` **6** + `seal_type` 6 + `cert_type` **5** + `payment_method` **4** + `group_dept` 4 + `review_dept_other` 4 + `return_status` 3 = **37 行**（V0.4 业务裁定后：合同类型去掉 `sales`、证照类型去掉 `bank_account_license`、付款方式去掉 `entrust`）。

---

## 12. 变更记录

| 版本 | 日期 | 修改说明 |
| --- | --- | --- |
| V0.4 | 2026-07-09 | 首版：给出 8 个字典类型共 40 行种子数据；明确 `payment_belong` / `planned_category` 为 checkbox 布尔字段而非字典项；统一 `dict_type` 命名为 `matter_category` / `payment_method` / `cert_type`；新增 `group_dept` 与 `review_dept_other`；给出枚举 vs 字典边界裁决表 |
| V0.4（业务裁定后修订） | 2026-07-09 | §0.4 待业务确认项**全部关闭**（D-01–D-10）；合同类型**不拆购销**（去 `sales`、加 PRD 8.2 对齐备注）、**保留劳务**；证照类型**去掉 `bank_account_license`**（5 值）；付款方式**保留「银行转账」**、**去掉 `entrust`**（4 值）；`seal_type` 明确 `cert_seal` 与 `cert_borrow` 并存；`return_status` 补「无需归还」终态备注口径（不参与超期提醒与催办）；`group_dept` 定位为**标签字典、禁止双写组织**，`review_dept_other` **同源于 `group_dept`**；字段 code 统一为 `plan_category` 与 `other_review_depts`（新增 §0.5）；种子行数 40 → **37**，自检新增 3.1 / 4.1 / 4.2 / 5.1 四项 |

---

> **交叉引用**：枚举取值见 [`enums.md`](enums.md)；流程与表单模板契约见 [`templates.md`](templates.md)；表结构以 [`data-model.md`](data-model.md) 为准；字段与字典绑定以 [`forms.md`](forms.md) 为准；打印规格以 [`DESIGN.md`](../DESIGN.md) 为准。
