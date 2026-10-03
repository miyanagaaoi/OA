# 集团OA审批系统 · 组织与人员批量导入规格说明书（import-spec.md）

| 项目 | 内容 |
| --- | --- |
| 文档用途 | 定义**组织架构、人员、组织负责人、岗位任职（一人多岗）、角色分配**五类批量导入模板的列契约、导入顺序、校验规则、错误报告格式、幂等与回滚、补偿控制与导出口径 |
| 对应排期 | `dev-plan-v0.3.md` 阶段 0 工作项 **0.6**：Excel 批量导入模板 + 校验规则说明（组织、人员、负责人；角色分配模板经裁定纳入本阶段） |
| 对应需求 | PRD **REQ-ADMIN-001**（维护组织架构、人员信息、**角色分配**；支持 Excel 批量导入与导出）；验收标准 **AC-57** |
| 对应业务语义 | PRD **5.1**（组织架构模型）、**5.5**（组织与人员变更的处理）、**5.4**（审批人解析）、**5.3**（数据域与字段级限制）、**AC-11 / AC-12** |
| 表结构依据 | [`data-model.md`](data-model.md) §2.1 `sys_org`、§2.2 `sys_user`、§2.3 `sys_org_leader`、§2.4 `sys_user_position`、§3.1 `sys_role`、§3.2 `sys_user_role` |
| 枚举依据 | [`enums.md`](enums.md) §10.1 事项类别五值、§4 实例状态、§1.1 命名约定 |
| 模板文件 | [`../oa-deploy/import/org.csv`](../oa-deploy/import/org.csv) · [`user.csv`](../oa-deploy/import/user.csv) · [`org_leader.csv`](../oa-deploy/import/org_leader.csv) · [`user_position.csv`](../oa-deploy/import/user_position.csv) · [`user_role.csv`](../oa-deploy/import/user_role.csv) |
| 校验工具 | [`../tools/check-import-csv.js`](../tools/check-import-csv.js)（零依赖 Node 脚本，用法 `node tools/check-import-csv.js oa-deploy/import`） |
| 版本 | V1.2 · 状态：评审稿 · 基准日期：2026-10-02（`remark` 列与角色分配模板落地后修订） |
| 维护边界 | **本文档不修改任何既有文档**。`remark` 列已由主控在 `data-model.md` 落地；仍存的分歧一律记入 §10，由主控统一回写 |

## 1. 文档用途与依据

### 1.1 用途

一期上线需要初始化「集团-公司-部门-科室」四级组织树与 **300+ 人员**（`dev-plan-v0.3.md` §6「数据初始化」）。手工录入易错且耗时长，因此第一阶段即交付 Excel/CSV 批量导入与校验报告（PRD 10.x 风险表）。本文档按「**填什么**（§3）→ **怎么校验**（§4、§5）→ **怎么落库**（§2、§6）→ **怎么不把流程搞挂**（§7、§8）」四段组织，并给出五张模板的逐列契约、校验规则、错误报告、幂等与回滚、补偿控制与导出口径。

### 1.2 权威依据（逐条引用）

| 依据 | 原文要点 | 本文档的落地位置 |
| --- | --- | --- |
| PRD **5.1 组织架构模型（REQ-ORG-001）** | 采用**集团-公司-部门-科室**四级架构；组织节点字段为名称、类型、上级节点、负责人、状态（启用/停用）；每个部门/科室可指定一个或多个负责人（支持一人多岗）；**集团层指定分管领导并可按业务线绑定**；一名员工可同时挂职于多个组织节点 | §3.2 `org.csv`、§3.4 `org_leader.csv`、§3.5 `user_position.csv`、§4.2、§4.5 |
| PRD **5.5 组织与人员变更的处理（REQ-ORG-002）** | 组织节点停用前必须处理完该节点全部在途单据；员工离职前必须处理完名下全部待办，系统提示未处理任务数量并强制先转办或改派；**批量调整（如公司重组）由系统管理员导入，导入前系统给出受影响在途单据清单，确认后方可执行** | §7、§8 |
| PRD **REQ-ADMIN-001** | 用户与组织管理：维护组织架构、人员信息、**角色分配**；支持 **Excel 批量导入与导出** | §2（含第 5 步角色分配）、§9（导出与模板列一致） |
| PRD **AC-57** | 用 Excel 模板导入 **300+ 人员与四级组织** → 校验报告列出**错误行与原因**；导入后**组织树、负责人关系与账号可用** | §5（报告格式）、§4（行级原因）、§2（顺序保证三者同时可用） |
| PRD **REQ-USER-004 / AC-44** | H5 与单据详情显示「**姓名 + 工号**」水印，不遮挡按钮与表单值 | §3.3 `employee_no`（必填 + 唯一，初始化导入是工号的唯一来源） |
| PRD **AC-11 / 5.4、AC-12 / 5.5** | 审批人空缺时**禁止发起**并提示「XX 节点无有效审批人」，不得静默跳过；离职前待办清空：管理员执行离职 → 系统提示未处理任务数量并阻止，要求先转办或改派 | §4.5 `W-ORG-014`、§8.1 |
| `data-model.md` §2 / §3 | `sys_org` / `sys_user` / `sys_org_leader` / `sys_user_position` / `sys_role` / `sys_user_role` 的 DDL、唯一键与外键（列名以该文档为准） | §3.7 映射总表 |
| `enums.md` §10.1 | 事项类别为**配置项五值**（`business` 经营 / `economy` 经济 / `admin` 行政 / `hr` 人力 / `invest` 投资），不参与路由 | §3.4 `business_line` **直接复用该五值的中文标签**，不做二次映射（T-06 已定稿） |
| `forms.md` 1.4 | 附件通用限制（50MB / 20 个 / 50 个 / 15 种格式）——**导入模板为纯文本，不承载附件** | §5.1 提示文案风格 |

### 1.3 术语与业务键

| 术语 | 定义 |
| --- | --- |
| **业务键** | 由人工在模板中填写的**稳定自然键**，导入程序据此 upsert；对组织是 `org_path`，对人员是 `account` |
| **`org_path`** | 组织全路径，从集团根节点起以 `/` 连接，如 `集团/公司A/部门1/科室1-1`；**全库唯一**，是组织的业务键 |
| **代理键** | 数据库自增 `id`（`sys_org.id` / `sys_user.id`），模板一律**不填写**，由导入程序解析生成 |
| **upsert** | 按业务键：存在则更新、不存在则新增（§6.1） |
| **dry-run** | 仅校验不导入：跑完 §4 全部规则 + §7/§8 数据库侧拦截，输出报告但不写任何业务表（§6.2） |

### 1.4 与既有文档的边界

- 本文档**只新增文件**，不改动 `data-model.md`、`enums.md`、`prd-0.1.md`、`forms.md`；枚举中文名 → code 的映射以 [`enums.md`](enums.md) 为准，本文档只做「模板中文取值 ↔ code」的转换说明。
- 模板列若在 DDL 中**没有直接对应列**（如 `org_code` 不存在），一律在 §3.7 标注「**由导入程序解析为 id**」或「**不落业务列**」，并把建议记入 §10，**不擅自改表**。

## 2. 导入顺序与前置条件

### 2.1 五步流水线（顺序不可调换）

```
① org.csv          组织架构      → sys_org
② user.csv         人员          → sys_user
③ org_leader.csv   组织负责人    → sys_org_leader（＋回填 sys_org.leader_id）
④ user_position.csv 岗位任职     → sys_user_position（＋回填 sys_user.position）
⑤ user_role.csv    角色分配      → sys_user_role（＋校验 sys_role.code）
```

每一步的输出是下一步的输入；**任一步失败则整批回滚，不进入下一步**（§5.3）。

### 2.2 逐步前置条件

| 步骤 | 文件 | 前置条件（不满足则本步整体拒绝） | 输出了什么（后续步骤依赖） |
| --- | --- | --- | --- |
| ① 组织 | `org.csv` | 库内已有且仅有 1 个集团根节点（首次导入由本文件自带）；`org_path` 全文件唯一；父路径已存在（同文件内可自底向上解析） | `org_path → sys_org.id` 映射表；`sys_org.path`（祖先路径串，如 `/1/12/135/`）与 `depth`（1 集团 / 2 公司 / 3 部门 / 4 科室） |
| ② 人员 | `user.csv` | ①已完成；`company_path`、`dept_path` 均能在①的映射表中解析 | `account → sys_user.id` 映射表；初始口令（**随机生成 + 加密清单线下分发**，§3.3 / §6.2） |
| ③ 负责人 | `org_leader.csv` | ①②均已完成；`org_path`、`user_account` 均能解析；负责人**不得为离职人员** | `sys_org_leader` 记录 + `sys_org.leader_id` 冗余回填（取该组织正职） |
| ④ 岗位 | `user_position.csv` | ①②已完成；`(user_account, org_path)` 唯一；每人**最多一个** `is_primary=是` | `sys_user_position` 记录 + `sys_user.position` 冗余回填（取主岗 `post_name`） |
| ⑤ 角色分配 | `user_role.csv` | ①②已完成；`role_code` 命中**已初始化角色集**（`sys_role` 由后台维护，不通过导入创建）；`scope_org_path`（可空）能解析 | `sys_user_role(user_id, role_id, scope_org_id)` |

> **顺序理由**：`sys_org_leader.user_id` 与 `sys_user_role.user_id` 都是 `sys_user.id` 外键（`data-model.md` 2.3 / 3.2），人员未落库即无法解析；`role_id` 又依赖后台已建角色（REQ-ADMIN-003，**不随导入创角色**）。

### 2.3 第 ⑤ 步（角色分配）的角色集与校准口径

本阶段交付 **`user_role.csv`**（**独立文件，不并入 `user.csv`**，避免一人多角色时行数膨胀）。`role_code` 必须命中**已初始化角色集**，脚本内置白名单（可用环境变量 `IMPORT_ROLE_CODES=a,b,c` 覆盖）：

| `role_code` | 角色名 | 数据域建议 |
| --- | --- | --- |
| `admin` | 系统管理员 | `group_all` |
| `company_admin` | 分公司流程管理员 | `company` |
| `employee` | 普通员工 | `self` |
| `dept_leader` | 部门/科室负责人 | `dept` |
| `branch_leader` | 分公司分管领导 | `company` |
| `subsidiary_gm` | 子公司总经理 | `company` |
| `finance_owner` | 集团归口（财务部）负责人 | `group_category` |
| `group_leader` | 集团分管领导 | `group_category` |
| `chairman` | 集团董事长 | `group_all` |

> **权威源**：上表与 `data-model.md` 3.1 `sys_role.code` 的列注释**一一对应**，该列注释即角色码的唯一权威定义（T-16 已确认）。导入**不创建角色**：库中不存在即报错 `E-ROLE-001`。
> 若实施时角色码有调整（例如新增业务线角色），以 `sys_role` 初始化脚本为准，并用环境变量 `IMPORT_ROLE_CODES=a,b,c` 覆盖校验白名单；校验器会在报告里回显 `roleCodeSource` 与 `roleCodes`，便于核对。

## 3. 五个模板逐列说明表

### 3.1 通用列规则

| 项目 | 规则 |
| --- | --- |
| 文件编码 | **UTF-8 带 BOM**（`EF BB BF`）。无 BOM 时 Excel 双击打开中文列名乱码 → 校验器报 `E-ENC-001` |
| 分隔符 | 英文逗号 `,`；**列名与枚举值中不得出现逗号** |
| 引号 | 值中含逗号、双引号或换行时必须用英文双引号包裹；内部双引号写 `""`（RFC4180） |
| 表头 | **第 1 行**，列名与顺序**逐字**固定，不得改名、增列、减列、换序 |
| 空值 | 允许留空表示 `NULL`（不适用）；**不得用空格或 `-` 代替空**；导入程序按 `trim` 处理首尾空格（校验器提示 `W-FMT-001`） |
| 行数 | 模板自带 **3–5 行示例数据**（用于人工照抄与 CI 校验）；正式数据行数不限，300+ 人应拆分为单文件一次导入 |
| 业务键 | 见 §1.3；代理键 `id` 一律不填；列名、`org_path` 段、`account` 区分大小写（`account` 建议统一小写）；模板一期不含日期列（`effective_from` / `effective_to` 由后台维护，T-08） |

### 3.2 `org.csv`（组织架构 → `sys_org`）

| 列名 | 含义 | 必填 | 取值/格式 | 校验规则 | 映射到（表.列） | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `org_path` | 组织全路径（唯一业务键） | **是** | `集团[/公司[/部门[/科室]]]`，段间 `/`，不含首尾 `/`、不含 `//`；≤255 字符 | 全文件唯一；父路径必须已在文件内或库内存在；`parent_path` 与之结构一致 | **不直接落列**——由导入程序解析为 `sys_org.parent_id` 链，并据此生成 `sys_org.path` 与 `sys_org.depth` | T-01 已定稿：**不新增** `sys_org.org_code`，`org_path` 即组织的唯一业务键 |
| `org_name` | 组织名称 | **是** | 中文/英文/数字，≤100 字符 | 同父节点下**允许重名**（唯一性由 `org_path` 保证），但重名会提示 | `sys_org.name` | PRD 5.1 组织节点字段「名称」 |
| `org_type` | 组织类型 | **是** | 枚举：`集团` / `公司` / `部门` / `科室` | 值必须在上列；层级必须为 集团→公司→部门→科室；**公司必须挂在集团下**；科室必须挂在部门下；部门可挂在公司或集团下（集团职能部门，PRD 5.1） | `sys_org.org_type` ← `集团=group` / `公司=company` / `部门=dept` / `科室=section` | 每行另决定 `sys_org.depth`：1/2/3/4 |
| `parent_path` | 父组织路径 | 条件必填 | `集团` 行**留空**；其余行必填 | 父路径必须存在于本文件或库中；必须等于 `org_path` 去掉最后一段 | `sys_org.parent_id`（解析为父节点 `id`） | 与 `org_path` 冗余但**必须一致**，防手改路径后父子错位 |
| `status` | 状态 | **是** | 枚举：`启用` / `停用` | 值必须在上列；停用节点不得作为人员 `dept_path`；停用前必须清空在途（PRD 5.5，服务端拦截） | `sys_org.status` ← `启用=active` / `停用=disabled` | PRD 5.1「状态（启用/停用）」 |
| `remark` | 备注 | 否 | ≤255 字符 | 仅长度校验（校验器 `E-ORG-013`） | **落库**到 `sys_org.remark`（`VARCHAR(255) NULL`，DDL 注释「备注（导入模板 remark 列落此处）」） | T-07 已确认：`remark` 列已落地 |

### 3.3 `user.csv`（人员 → `sys_user`）

| 列名 | 含义 | 必填 | 取值/格式 | 校验规则 | 映射到（表.列） | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `account` | 登录名（唯一业务键） | **是** | 8–64 位，**字母开头**，仅含字母/数字/下划线/点/连字符 | 全文件唯一；库内唯一（`sys_user.account` 唯一键）；不合规即拒绝 | `sys_user.account` | 首次登录**强制修改密码**；不含 `@` 与空格 |
| `employee_no` | **工号**（水印使用） | **是** | 1–32 字符，仅含字母、数字与 `-`（例 `A0001`） | 非空；≤32 字符；**全文件唯一**，且库内唯一 | `sys_user.employee_no` | **DDL 既有列**（`employee_no VARCHAR(32) NULL COMMENT '工号（水印使用）'`）；初始化导入是工号的**唯一来源**，REQ-USER-004 / AC-44 水印取「姓名 + 工号」 |
| `name` | 姓名 | **是** | ≤50 字符 | 非空；长度 | `sys_user.name` | 与 `employee_no` 共同构成水印内容（REQ-USER-004） |
| `phone` | 手机号 | **是**（T-04 已定稿） | 11 位大陆手机号，`^1[3-9]\d{9}$` | 格式；**唯一性不校验**（同号可被家庭成员共用，属业务问题） | `sys_user.phone`——**加密存储**，展示按角色脱敏（PRD 5.3：仅本人与系统管理员可见完整值） | 手机号是账号安全链路的一环，不允许为空（T-04 已关闭） |
| `email` | 邮箱 | 否 | `name@example.com`，≤128 字符 | 非空时校验格式与长度 | `sys_user.email` | 站内信之外的唯一主动提醒渠道（`enums.md` §8 渠道矩阵） |
| `company_path` | 所属公司路径 | **是** | 必须是 `org.csv` 中已存在的路径 | 必须存在；且其 `org_type` 只能是 `公司`；**集团本部人员填 `集团`** | `sys_user.company_id`（解析为 `sys_org.id`） | 数据域判定依据（PRD 5.3：子公司总经理/分公司管理员按本公司隔离） |
| `dept_path` | 所属部门/科室路径 | 否 | 必须是 `org.csv` 中已存在的路径 | 必须存在；`org_type` ∈ {`部门`,`科室`}；必须位于 `company_path` 子树内（等于它或以它加 `/` 开头） | `sys_user.org_id`（解析为 `sys_org.id`；空则 `NULL`） | 主归属组织节点；与 `user_position.csv` 的主岗应一致（T-12 已定稿） |
| `status` | 状态 | **是** | 枚举：`在职` / `离职` | 值必须在上列；`离职` 行在导入时若名下有未处理待办 → **拒绝并给出待办数量**（PRD 5.5 / AC-12，§8.1） | `sys_user.status` ← `在职=active` / `离职=resigned` | DDL 另含 `disabled`（停用）；T-05 已定稿：**停用不通过批量导入设置**，由后台单条操作 |
| `remark` | 备注 | 否 | ≤255 字符 | 仅长度校验（校验器 `E-USER-012`） | **落库**到 `sys_user.remark`（`VARCHAR(255) NULL`） | T-07 已确认：`remark` 列已落地 |
| （隐含） | 初始口令 | — | 由导入程序批量生成**随机初始口令**：≥8 位、含字母与数字，符合 REQ-NFR-005 | 首登强制改密；连续失败 **5 次锁定 15 分钟** | `sys_user.password_hash`——**只存加盐哈希，禁止明文** | T-03 已定稿：**随机口令 + 加密清单线下分发**，不通过邮件明文发送（§6.2） |

### 3.4 `org_leader.csv`（组织负责人 → `sys_org_leader`）

| 列名 | 含义 | 必填 | 取值/格式 | 校验规则 | 映射到（表.列） | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `org_path` | 被绑定负责人的组织 | **是** | 必须是 `org.csv` 中已存在的路径 | 必须存在 | `sys_org_leader.org_id`（解析为 id） | 权威来源在 `sys_org_leader`，`sys_org.leader_id` 仅冗余 |
| `user_account` | 负责人登录名 | **是** | 必须是 `user.csv` 中已存在的账号 | 必须存在；**不得为 `离职` 人员** | `sys_org_leader.user_id`（解析为 id） | 一人可在多个组织任负责人（一人多岗，PRD 5.1） |
| `leader_type` | 负责人类型 | **是** | 枚举：`正职` / `副职` | 值必须在上列；**同一组织同一业务线只能有一个正职**，副职不限 | `sys_org_leader.leader_type` ← `正职=primary` / `副职=deputy` | 审批人解析取「正职」；`data-model.md` 默认值 `primary`；口径见 §4.4（T-09 已定稿） |
| `sort` | 排序号 | **是** | 0–9999 整数；留空按 0 | 整数校验 | `sys_org_leader.sort_no` | PRD 7.1 概览写 `sort`，DDL 实际列名 `sort_no`（以 `data-model.md` 为准） |
| `business_line` | 分管业务线（= 事项类别） | 否（**仅集团层填**） | 枚举（事项类别五值中文标签）：`经营` / `经济` / `行政` / `人力` / `投资` | 值必须在上列；**仅当 `org_path` 的 `org_type=集团` 时允许填写**，其余组织填写即报错 | `sys_org_leader.category` ← `经营=business` / `经济=economy` / `行政=admin` / `人力=hr` / `投资=invest` | 依据 PRD 5.1「集团层指定分管领导，**按业务线绑定**」与 5.4「集团分管领导按事项类别匹配」；**业务线直接复用 `enums.md` §10.1 的事项类别五值，不新增枚举**（T-06 已定稿）。口径：**财务分管领导在系统中等同于「经济」类分管领导**（Q10 后五个事项类别统一归口财务部） |
| `remark` | 备注 | 否 | ≤255 字符 | 仅长度校验（校验器 `E-LEAD-010`） | **落库**到 `sys_org_leader.remark`（`VARCHAR(255) NULL`） | T-07 已确认：`remark` 列已落地 |
| （隐含） | 岗位名 / 生效区间 | — | 模板一期未开放（T-08 已定稿） | — | `sys_org_leader.duty_title` / `effective_from` / `effective_to` | 缺省 `NULL`；如「财务分管领导」，二期按需纳入 |

### 3.5 `user_position.csv`（岗位任职 / 一人多岗 → `sys_user_position`）

| 列名 | 含义 | 必填 | 取值/格式 | 校验规则 | 映射到（表.列） | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `user_account` | 登录名 | **是** | 必须是 `user.csv` 中已存在的账号 | 必须存在；同人在本文件可出现多行（一人多岗） | `sys_user_position.user_id`（解析为 id） | PRD 5.1「一名员工可同时挂职于多个组织节点」 |
| `org_path` | 任职组织 | **是** | 必须是 `org.csv` 中已存在的路径 | 必须存在；**`(user_account, org_path)` 不得重复**（`uk_user_org`） | `sys_user_position.org_id`（解析为 id） | 唯一键依据 `data-model.md` 2.4 |
| `post_name` | 岗位名称 | **是** | ≤50 字符 | 非空；长度 | `sys_user_position.position` | 主岗的 `post_name` 同时回填 `sys_user.position`（职务名称） |
| `is_primary` | 是否主岗 | **是** | 枚举：`是` / `否` | 值必须在上列；**同一 `user_account` 最多一条 `是`** | `sys_user_position.is_primary` ← `是=1` / `否=0` | 主岗用于 `sys_user.org_id` / `company_id` 的一致性核对（§4.5） |
| `remark` | 备注 | 否 | ≤255 字符 | 仅长度校验（校验器 `E-POS-007`） | **落库**到 `sys_user_position.remark`（`VARCHAR(255) NULL`） | T-07 已确认：`remark` 列已落地 |

### 3.6 `user_role.csv`（角色分配 → `sys_user_role` ＋ `sys_role`）

| 列名 | 含义 | 必填 | 取值/格式 | 校验规则 | 映射到（表.列） | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `user_account` | 登录名 | **是** | 必须是 `user.csv` 中已存在的账号 | 必须存在（`E-ROLE-002`） | `sys_user_role.user_id`（解析为 id） | 同一人可多行（多角色） |
| `role_code` | 角色码 | **是** | 小写蛇形 `^[a-z][a-z0-9_]{1,31}$`（`enums.md` §1.1），且必须属于**已初始化角色集**（§2.3 白名单，权威源为 `sys_role.code`） | 白名单命中（`E-ROLE-001`） | `sys_role.code` → `sys_user_role.role_id`（解析为 id） | **角色码不在模板中创造**；默认白名单见 §2.3，与 `sys_role.code` 列注释一一对应（T-16 已确认） |
| `scope_org_path` | 角色生效的组织范围（数据域绑定） | 否 | 必须是 `org.csv` 中已存在的路径；**留空 = 按角色默认数据域**（如 `employee` 为本人、`subsidiary_gm` 为本公司） | 非空时必须存在（`E-ROLE-004`） | `sys_user_role.scope_org_id`（解析为 id；空则 `NULL`） | 对应 `sys_user_role.scope_org_id` 注释「该角色生效的组织范围；为空=按角色默认」 |
| `remark` | 备注 | 否 | ≤255 字符 | 仅长度校验（`E-ROLE-005`） | **落库**到 `sys_user_role.remark`（`VARCHAR(255) NULL`，T-15 已确认并落地） | DDL 注释「备注（导入模板 remark 列落此处）」 |

> **唯一性口径（与库约束一致）**：`sys_user_role` 的唯一键为 **`uk_sys_user_role (user_id, role_id, scope_org_key)`**，其中 `scope_org_key = IFNULL(scope_org_id, 0)`（STORED 生成列）。因为 **MySQL 唯一键对 NULL 不去重**，若直接用可空的 `scope_org_id` 参与唯一键，「同一人 + 同一角色 + 空数据域」可被重复插入；生成列把空值折算为 `0`，从而与导入校验 `(user_account, role_code, scope_org_path)` 不得重复（`E-ROLE-003`）**完全对齐**。

### 3.7 模板列 → 表列映射总表

| 模板列 | 目标表.列 | 处置 | 说明 |
| --- | --- | --- | --- |
| `org.csv.org_path` | `sys_org`（无同名列） | **由导入程序解析为 id** | 作为业务键用于 upsert；同时解析出 `parent_id`、生成 `path` 与 `depth` |
| `org.csv.parent_path` | `sys_org.parent_id` | 解析 | 字符串路径 → 父节点 `id`；集团行为 `NULL` |
| `org.csv.org_type` | `sys_org.org_type` | 枚举转 code | `集团=group` / `公司=company` / `部门=dept` / `科室=section` |
| `org.csv.status` | `sys_org.status` | 枚举转 code | `启用=active` / `停用=disabled` |
| `org.csv.remark` | `sys_org.remark` | 直落 | `VARCHAR(255) NULL`，DDL 注释「备注（导入模板 remark 列落此处）」 |
| `user.csv.account` / `name` / `email` | `sys_user.account` / `name` / `email` | 直落 | `account` 为唯一键；`name` 与 `employee_no` 构成水印 |
| `user.csv.employee_no` | `sys_user.employee_no` | 直落 | **DDL 既有列**（`VARCHAR(32) NULL`，注释「工号（水印使用）」）；模板定为**必填 + 唯一**，导入后不会为 `NULL` |
| `user.csv.phone` | `sys_user.phone` | 直落（加密） | 加密存储 + 按角色脱敏（PRD 5.3） |
| `user.csv.company_path` | `sys_user.company_id` | **解析为 id** | 路径 → `sys_org.id` |
| `user.csv.dept_path` | `sys_user.org_id` | **解析为 id** | 路径 → `sys_org.id`；空则 `NULL` |
| `user.csv.status` | `sys_user.status` | 枚举转 code | `在职=active` / `离职=resigned` |
| `user.csv.remark` | `sys_user.remark` | 直落 | `VARCHAR(255) NULL` |
| `org_leader.csv.org_path` / `user_account` | `sys_org_leader.org_id` / `user_id` | **解析为 id** | — |
| `org_leader.csv.leader_type` | `sys_org_leader.leader_type` | 枚举转 code | `正职=primary` / `副职=deputy` |
| `org_leader.csv.sort` | `sys_org_leader.sort_no` | 改名直落 | DDL 列名为 `sort_no` |
| `org_leader.csv.business_line` | `sys_org_leader.category` | 枚举转 code | `经营=business` / `经济=economy` / `行政=admin` / `人力=hr` / `投资=invest`；业务线**直接复用事项类别五值**，不做二次映射（T-06 已定稿） |
| `org_leader.csv.remark` | `sys_org_leader.remark` | 直落 | `VARCHAR(255) NULL` |
| `user_position.csv.remark` | `sys_user_position.remark` | 直落 | `VARCHAR(255) NULL` |
| `user_role.csv.user_account` | `sys_user_role.user_id` | **解析为 id** | — |
| `user_role.csv.role_code` | `sys_role.code` → `sys_user_role.role_id` | **解析为 id** | 角色码必须已在 `sys_role` 中初始化；白名单见 §2.3 |
| `user_role.csv.scope_org_path` | `sys_user_role.scope_org_id` | **解析为 id** | 路径 → `sys_org.id`；空则 `NULL`（按角色默认数据域） |
| `user_role.csv.remark` | `sys_user_role.remark` | 直落 | `VARCHAR(255) NULL`（T-15 已确认并落地） |
| `user_position.csv.user_account` / `org_path` | `sys_user_position.user_id` / `org_id` | **解析为 id** | — |
| `user_position.csv.post_name` | `sys_user_position.position` | 改名直落 | 主岗同时回填 `sys_user.position` |
| `user_position.csv.is_primary` | `sys_user_position.is_primary` | 枚举转布尔 | `是=1` / `否=0` |

> **`remark` 列（T-07 / T-15 已确认）**：`sys_org` / `sys_user` / `sys_org_leader` / `sys_user_position` / `sys_user_role` **五张表**的 `remark VARCHAR(255) NULL` 均已由主控落地，模板 `remark` 列**全部直接落库**（长度校验统一 ≤255）。**`org_code` 说明**：`sys_org` **没有** `org_code` 列，T-01 已定稿以 `org_path` 作为组织的唯一业务键。

## 4. 校验规则清单（可判定）

### 4.1 文件与编码级

| 编号 | 规则 | 级别 |
| --- | --- | --- |
| `E-FILE-001` | 五个模板文件必须存在且文件名大小写完全一致 | error |
| `E-ENC-001` | 必须以 UTF-8 BOM（`EF BB BF`）开头 | error |
| `E-ENC-002` | 必须能被 UTF-8 正确解码（不得含替换字符 `U+FFFD`、不得为 UTF-16） | error |
| `E-HDR-001` | 表头列名与顺序必须与 §3 规格**逐字一致** | error |
| `E-HDR-002` | 必须存在表头行 | error |
| `E-ROW-001` / `E-ROW-002` | 每行列数等于表头列数；数据行数为 3–5 行示例数据 | error |
| `E-ROW-003` / `E-ROW-004` | 不得存在空行；数据区不得重复出现表头行 | error |
| `W-FMT-001` | 单元格首尾存在空格（按 trim 处理） | warning |

### 4.2 组织级（`org.csv`）

| 编号 | 规则 | 级别 |
| --- | --- | --- |
| `E-ORG-001` | `org_path` 全文件唯一 | error |
| `E-ORG-002` | `parent_path` 必须在本文件（或已导入的库）中存在 | error |
| `E-ORG-003` | `parent_path` 必须等于 `org_path` 去掉最后一段 | error |
| `E-ORG-004` / `E-ORG-005` | 集团行的 `parent_path` 必须为空；非集团行的 `parent_path` 必填 | error |
| `E-ORG-006` | 层级约束：集团→公司→部门→科室；**公司必须挂在集团下**；科室必须挂在部门下；部门可挂公司或集团 | error |
| `E-ORG-007` | `org_type` ∈ {`集团`,`公司`,`部门`,`科室`} | error |
| `E-ORG-008` | `org_path` 非空、无空段、无首尾 `/`、≤255 字符 | error |
| `E-ORG-009` | `status` ∈ {`启用`,`停用`} | error |
| `E-ORG-012` / `E-ORG-013` | `org_name` 必填且 ≤100 字符；`remark` ≤255 字符（`sys_org.remark` 列宽） | error |
| `E-ORG-015` | 整棵组织树有且仅有 **1 个**集团根节点 | error |
| `E-ORG-010` | 组织停用前必须清空该节点全部在途单据（**服务端查库校验**，PRD 5.5） | error |
| `W-ORG-014` | 部门/科室**未设正职负责人**（审批人候选为空 → 该部门成员发起时被拦截，AC-11） | warning |
| `W-ORG-016` | 父组织已停用，子节点导入后不可作为发起归属 | warning |
| `W-ORG-017` | 停用组织下仍有在职人员 | warning |
| `E-ORG-020` | **数据域越权（fail-closed）**：该行的 `parent_path`（父组织已在库）或 `org_path`（节点已存在）**不在导入人的数据域内**（分公司管理员只能导入本公司子树）→ **整批拒绝**，不做「跳过该行继续导入」 | error |

> **`E-ORG-020` 提示文案口径**（与 `ImportCodes` / `ImportScopeGuard` 一致）：「该行超出当前导入人的数据域（组织路径 `集团/公司B/部门3`），已按 fail-closed 拒绝整批导入」，修正建议「该行超出当前导入人的数据域（分公司管理员只能导入本公司子树），已按 fail-closed 拒绝」。判定用**组织真实路径前缀**（`/12/`），与 `DataScopeContext` 的口径一致；系统管理员（系统口径）不做该判定。

### 4.3 人员级（`user.csv`）

| 编号 | 规则 | 级别 |
| --- | --- | --- |
| `E-USER-001` | `account` 必填，8–64 位、字母开头，仅含字母/数字/下划线/点/连字符 | error |
| `E-USER-002` | `account` 全文件唯一，且库内唯一（`sys_user.account`） | error |
| `E-USER-014` | `employee_no` **必填**、1–32 字符、仅含字母/数字/`-` | error |
| `E-USER-015` | `employee_no` **全文件唯一**，且库内唯一（水印 REQ-USER-004 / AC-44 与人事核对依赖） | error |
| `E-USER-003` | `phone` 必填且符合 11 位大陆手机号格式 `^1[3-9]\d{9}$` | error |
| `E-USER-004` | `email` 非空时格式合法且 ≤128 字符 | error |
| `E-USER-005` / `E-USER-008` | `company_path` 必填、能在 `org.csv` 中解析，且只能指向 `公司`（集团本部人员填 `集团`） | error |
| `E-USER-006` | `dept_path` 非空时必须能在 `org.csv` 中解析，且为部门/科室 | error |
| `E-USER-007` | `status` ∈ {`在职`,`离职`} | error |
| `E-USER-009` | **离职前必须清空名下待办**（服务端查库校验，PRD 5.5 / AC-12）：名下有 `flow_task.status='pending'` 或 `flow_node_instance.status='active'` 的任务 → 拒绝并给出数量 | error |
| `E-USER-010` | `name` 必填且 ≤50 字符 | error |
| `E-USER-011` | `dept_path` 必须位于 `company_path` 子树内 | error |
| `E-USER-012` | `remark` ≤255 字符 | error |
| `E-USER-013` | 账号在库中已存在但姓名不一致（防止误合并到他人账号） | error |
| `E-USER-020` | **数据域越权（fail-closed）**：`company_path` 或 `dept_path` 指向的组织**不在导入人的数据域内** → **整批拒绝** | error |

### 4.4 负责人级（`org_leader.csv`）

| 编号 | 规则 | 级别 |
| --- | --- | --- |
| `E-LEAD-001` | `org_path` 必填且能在 `org.csv` 中解析 | error |
| `E-LEAD-002` | `user_account` 必填且能在 `user.csv` 中解析 | error |
| `E-LEAD-003` | `leader_type` ∈ {`正职`,`副职`} | error |
| `E-LEAD-004` | **同一组织（同一业务线分组）只能有一个正职**；多副职允许 | error |
| `E-LEAD-005` | `business_line` ∈ {`经营`,`经济`,`行政`,`人力`,`投资`}（事项类别五值中文标签）或留空 | error |
| `E-LEAD-006` | 负责人不得为 `离职` 人员 | error |
| `E-LEAD-007` | `sort` 为 0–9999 整数或留空 | error |
| `E-LEAD-008` | `business_line` 仅允许填在 `org_type=集团` 的节点上 | error |
| `E-LEAD-009` | `(org_path, user_account, leader_type, business_line)` 四元组不得重复（对应 `uk_org_leader`） | error |
| `E-LEAD-020` | **数据域越权（fail-closed）**：该行 `org_path` 的组织，或 `user_account` 所属人员归属的公司，**不在导入人的数据域内** → **整批拒绝** | error |

> **「一级部门/科室只能有一个正职」的判定口径（T-09 已定稿）**：以 **`org_path` + `business_line` 为分组键**，每组至多 1 条 `leader_type=正职`；`business_line` 留空视为同一组。因此「集团」节点可同时存在「经济线正职」与「经营线正职」（PRD 5.1 按业务线绑定分管领导），但同一业务线内不得出现两个正职。

### 4.5 岗位级（`user_position.csv`）

| 编号 | 规则 | 级别 |
| --- | --- | --- |
| `E-POS-001` | `org_path` 必填且能在 `org.csv` 中解析 | error |
| `E-POS-002` | `user_account` 必填且能在 `user.csv` 中解析 | error |
| `E-POS-003` | `is_primary` ∈ {`是`,`否`} | error |
| `E-POS-004` | **同一 `(user_account, org_path)` 不得重复**（`uk_user_org`） | error |
| `E-POS-005` | **每人最多一个 `is_primary=是`** | error |
| `E-POS-006` | `post_name` 必填且 ≤50 字符 | error |
| `E-POS-007` | `remark` ≤255 字符（`sys_user_position.remark` 列宽） | error |
| `W-POS-008` | 在职人员的 `user.csv.dept_path` 未在岗位表出现（建议补主岗记录） | warning |
| `W-POS-009` | 岗位组织不在该员工所属公司子树内（跨公司兼职需确认） | warning |
| `E-POS-020` | **数据域越权（fail-closed）**：该行 `org_path` 的组织，或 `user_account` 所属人员归属的公司，**不在导入人的数据域内** → **整批拒绝** | error |

### 4.6 角色分配级（`user_role.csv`）

| 编号 | 规则 | 级别 |
| --- | --- | --- |
| `E-ROLE-001` | `role_code` 必填、格式为小写蛇形 `^[a-z][a-z0-9_]{1,31}$`，且**属于已初始化角色集**（§2.3 白名单，可用 `IMPORT_ROLE_CODES` 覆盖） | error |
| `E-ROLE-002` | `user_account` 必填且能在 `user.csv` 中解析 | error |
| `E-ROLE-003` | **同一 user + role + 空数据域视为同一条**：`(user_account, role_code, scope_org_path)` 不得重复，空 `scope_org_path` 归一为同一组（对应 `uk_sys_user_role (user_id, role_id, scope_org_key)`，`scope_org_key = IFNULL(scope_org_id, 0)`；**MySQL 唯一键对 NULL 不去重，故 NULL 按 0 参与唯一键**）——**导入校验与库约束完全一致** | error |
| `E-ROLE-004` | `scope_org_path` 非空时必须能在 `org.csv` 中解析（空 = 按角色默认数据域） | error |
| `E-ROLE-005` | `remark` ≤255 字符（**落库** `sys_user_role.remark VARCHAR(255)`，T-15 已落地） | error |
| `E-ROLE-020` | **数据域越权（fail-closed）**：`user_account` 所属人员归属的公司，或非空 `scope_org_path` 指向的组织，**不在导入人的数据域内** → **整批拒绝** | error |

> 角色分配**不校验人员状态**（离职人员的历史角色由后台清理）；但 `user_account` 不存在于 `user.csv` 时仍按 `E-ROLE-002` 拒绝。

> **数据域越权（`E-*-020`）的统一口径（阶段 1 收口定稿）**
> 1. **fail-closed，整批拒绝**：任一数据行越域即判定整批失败（与 §5.3 阶段 B 同一处置），**不做「跳过越域行、其余照常导入」**——跳过会让调用方以为导入成功，属静默放行（`ImportScopeGuard` 类注释即此约定）。
> 2. **判定主体是「导入人的数据域」**：分公司流程管理员 = 本公司子树 ∪ 本部门子树 ∪ 归口部门子树；系统管理员走**系统口径**（`DataScopeContext.isBypass()`），不受该组错误码约束。
> 3. **判定用组织真实路径前缀**（`/12/`），不是名称路径（`集团/公司A`）——名称可改，真实路径不可伪造。
> 4. **与库读取口径的关系**：导入的解析查库在**系统口径**下执行（否则分公司管理员看不到域外组织，会得到 `E-ORG-002`「父路径不存在」这类**误导性**错误），授权判定因此集中在 `ImportScopeGuard` 逐行显式执行；即「读得到、但写不进去」，错误码必须说清是数据域问题。
> 5. 号段：`020` 为数据域越权专用码，五类模板各占一个（`E-ORG-020` / `E-USER-020` / `E-LEAD-020` / `E-POS-020` / `E-ROLE-020`），**不复用、不改义**；`021–029` 预留。
> 6. **不由 CLI 校验器判定**：越权与否取决于**调用人的数据域**，`tools/check-import-csv.js`（离线、无调用人上下文）无法判定，故这五个码不在其覆盖范围（见 §4.8 / §11.1），由服务端 dry-run 逐行执行。

### 4.7 跨文件与规则一览（可自动判定的全部硬约束）

| # | 规则 | 归属章节 |
| --- | --- | --- |
| 1 | `org_path` 唯一；`parent_path` 必须已存在 | §4.2 |
| 2 | 四级层级约束（集团→公司→部门→科室），**公司必须挂在集团下** | §4.2 |
| 3 | `account` 唯一且 ≥8 位、字母开头；`phone` 11 位手机号格式 | §4.3 |
| 4 | 同一 `(user_account, org_path)` 在岗位表不得重复 | §4.5 |
| 5 | **一级部门/科室只能有一个正职**（多副职允许）；`is_primary` 每人最多一个「是」 | §4.4 / §4.5 |
| 6 | `user.csv` 的 `company_path` / `dept_path` 可在 `org.csv` 找到 | §4.3 |
| 7 | `org_leader` / `user_position` / `user_role` 的 `org_path`、`user_account`、`scope_org_path` 可在前两个文件找到 | §4.4 / §4.5 / §4.6 |
| 8 | `employee_no` 必填、≤32 字符、仅字母数字与 `-`，且全文件唯一 | §4.3 |
| 9 | `role_code` 命中已初始化角色集；`(user_account, role_code, scope_org_path)` 唯一（空数据域归一为同一组） | §4.6 |

### 4.8 校验器与规则的对应关系（详细用法与退出码见 §11.1）

[`../tools/check-import-csv.js`](../tools/check-import-csv.js) 覆盖 §4.1–§4.6 中标注为「文件内可判定」的**全部规则**（五份文件；即除 `E-ORG-010`、`E-USER-009`、`E-USER-013`、`W-ORG-014` 四项需查库/查流程的规则，以及五个 `E-*-020` 数据域越权码外全部覆盖）。五类**服务端/后台**规则由导入程序在 dry-run 阶段执行（§6.2），校验器不臆测；`E-*-020` 依赖**调用人的数据域**，离线校验器没有该上下文，故一律由服务端逐行判定。

## 5. 错误报告格式

### 5.1 示例错误报告表（人工可读版）

> 报告一次列全**所有**错误行，不因首行失败而中断（全量校验）；提示文案采用 `forms.md` 1.3 的直白句式（「请填写{标签}」风格）。

| 行号 | 列 | 值 | 错误码 | 提示文案 |
| --- | --- | --- | --- | --- |
| 3 | `account` | `lisi` | `E-USER-001` | 账号长度不足 8 位：`lisi`。请使用「字母 + 数字」的 8–64 位登录名，例如 `lisi0002` |
| 4 | `account` | `wangwu03` | `E-USER-002` | 账号重复：`wangwu03` 已在第 3 行出现。登录名唯一，请合并或改名 |
| 4 | `employee_no` | `A0001` | `E-USER-015` | 工号重复：`A0001` 已在第 3 行出现。工号唯一，请核对人事花名册（同一行可同时有多条错误） |
| 4b | `employee_no` | （空） | `E-USER-014` | 工号不能为空：水印需要「姓名 + 工号」（REQ-USER-004 / AC-44） |
| 5 | `phone` | `1390000` | `E-USER-003` | 手机号格式不正确：`1390000`。请填写 11 位大陆手机号，例如 `13800000003` |
| 9 | `org_path` | `集团/公司B/财务部` | `E-ORG-002` | 父路径不存在：`集团/公司B`。请先导入父组织行，或修正 `parent_path` |
| 14 | `user_account` | `sunqi0005` | `E-LEAD-006` | 「孙七」状态为 `离职`，不得作为组织负责人（审批人解析会取到无效候选人） |
| 15 | （行级） | `集团/公司A/财务部` | `E-ORG-010` | 组织停用前必须清空在途单据：该节点下仍有 **3** 张在途单据（PRD 5.5） |
| 16 | （行级） | `sunqi0005` | `E-USER-009` | 离职前必须清空名下待办：该员工名下仍有 **2** 条未处理待办，请先转办或由系统管理员改派（AC-12） |
| 17 | `role_code` | `group_exec` | `E-ROLE-001` | 角色码不在已初始化角色集内：`group_exec`。允许值 `group_leader`（集团分管领导）等，见规格书 §2.3 |

### 5.2 错误码字典

| 前缀 | 含义 | 码段 |
| --- | --- | --- |
| `E-FILE` / `E-ENC` / `E-HDR` / `E-ROW` | 文件、编码、表头、行结构 | §4.1 |
| `E-ORG` | 组织架构（`sys_org`） | §4.2 |
| `E-USER` | 人员（`sys_user`） | §4.3 |
| `E-LEAD` | 组织负责人（`sys_org_leader`） | §4.4 |
| `E-POS` | 岗位任职（`sys_user_position`） | §4.5 |
| `E-ROLE` | 角色分配（`sys_user_role` ＋ `sys_role.code`） | §4.6 |
| `E-{ORG\|USER\|LEAD\|POS\|ROLE}-020` | **数据域越权**（行超出导入人数据域 → fail-closed **整批拒绝**；系统管理员不受此约束） | §4.2–§4.6、§5.3 |

**码段分配约定**：`E-ORG-001..019`、`E-USER-001..019`、`E-LEAD-001..019`、`E-POS-001..019`、`E-ROLE-001..019` 为保留段；已分配码见 §4，未分配号段预留给后续规则（**不复用、不改义**，与 `enums.md` §0.1 维护规则一致）。

**`-020` 号段（阶段 1 收口新增，五类模板各一个）**：`E-ORG-020` / `E-USER-020` / `E-LEAD-020` / `E-POS-020` / `E-ROLE-020` 统一表示**数据域越权**，语义与触发条件见 §4.2–§4.6 各自的表格行与统一口径说明。三条硬约定：
1. **级别一律 `error`**（不是 warning）：越域行不是「可疑数据」，而是**调用人无权写入的数据**；若降级为 warning，就会在 §5.3 阶段 B 被放行，等于给越权留通道。
2. **处置一律「整批拒绝 + 错误零落库」**：与 `E-ORG-010` / `E-USER-009` 等服务端规则同属阶段 A 发现的 error，任一命中即阶段 B 判定整批失败。
3. **提示文案必须点明「数据域」二字并给出越域的组织路径**：不得复用 `E-ORG-002`（父路径不存在）或 `E-USER-005`（公司路径解析失败）——那些会让人误以为是数据错误，实际是权限边界。文案模板：「该行超出当前导入人的数据域（组织路径 `集团/公司B/部门3`），已按 fail-closed 拒绝整批导入」。

### 5.3 「全量校验后再导入」与事务边界

| 阶段 | 行为 | 失败处置 |
| --- | --- | --- |
| **阶段 A：全量校验** | 逐行跑 §4 全部规则（含服务端查库规则，**含逐行数据域判定 `E-*-020`**） | 收集**全部**错误，不提前中断；报告须列全 |
| **阶段 B：判定** | 只要存在 ≥1 个 `error` → 判定整批失败（**含任意一条 `E-*-020` 数据域越权**） | **整批不落库**（`BEGIN … ROLLBACK`），库内数据保持导入前状态 |
| **阶段 C：导入** | 全部 `error` 为 0 时，在**单个事务**内按 §2.1 顺序写四张表 | 事务内任一步失败 → `ROLLBACK`，返回失败行与原因 |
| **阶段 D：留痕** | 无论成功失败，写 `sys_log`（`action=import_org_user`、`target_type=org_import`、`before_json`/`after_json`、行数统计） | 失败批次同样留痕 |
**为什么必须整批事务**：组织树是 `sys_org.parent_id` 自引用树 ＋ `sys_user.company_id`/`org_id` 外键；半批落库会产生**断链的孤儿节点**与**归属错误的人员**，比"什么也没导入"更难恢复。

### 5.4 报告统计与修正建议

报告头部必须给出：

| 字段 | 说明 |
| --- | --- |
| `totalRows` | 五文件数据行总数（含已通过行） |
| `passedRows` | 通过校验的行数 |
| `failedRows` | 存在 error 的行数（**按行去重**，一行多错只计一行） |
| `errors` / `warnings` | 错误 / 警告条目总数（一行可多条） |
| `byCode` | 错误码分布（`[{code, count, suggestion}]`），用于「一次修一类问题」 |
| `suggestions` | 按错误码去重的**修正建议**清单（可直接复制给填报人）；`dryRun: true` 表示仅校验未落库 |

**修正建议示例**（自动生成）：

```json
"suggestions": [
  { "code": "E-USER-001", "count": 47, "suggestion": "account 必填，8–64 位、字母开头，仅含字母/数字/下划线/点/连字符" },
  { "code": "E-ORG-002", "count": 3,  "suggestion": "先导入其父组织行，或修正拼写；父组织不存在时本行必定失败" } ]
```

### 5.5 机器可读报告结构（与本仓库校验器一致）

`node tools/check-import-csv.js oa-deploy/import` 输出如下 JSON（stdout）；**存在 error 时进程退出码为 1**，可直接串入 CI：

```json
{
  "tool": "check-import-csv", "version": "1.2.0", "ok": true, "dir": "oa-deploy/import",
  "spec": { "encoding": "UTF-8 with BOM (EF BB BF)", "delimiter": ",", "headerRow": 1, "sampleRows": "3-5",
            "requiredFiles": ["org.csv","user.csv","org_leader.csv","user_position.csv","user_role.csv"],
            "roleCodeSource": "builtin-default", "roleCodes": ["sys_admin","employee","…"] },
  "files": [{ "file": "org.csv", "columns": 6, "dataRows": 5 }, { "file": "user_role.csv", "columns": 4, "dataRows": 5 }],
  "suggestions": [],
  "findings": [{ "severity": "error", "code": "E-ROLE-001", "file": "user_role.csv", "line": 4, "column": "role_code",
                 "value": "group_exec", "message": "角色码不在已初始化角色集内：group_exec" }],
  "summary": { "filesChecked": 5, "filesFound": 5, "dataRows": 25, "errors": 0, "warnings": 0 }
}
```

| 退出码 | 含义 |
| --- | --- |
| `0` | 无 error（可含 warning），允许进入导入 |
| `1` | 存在 error，**整批拒绝** |
| `2` | 用法错误（未传目录）或目录不存在 |

## 6. 幂等与回滚

### 6.1 以业务键 upsert

| 文件 | 业务键 | 存在时的行为 | 不存在时 |
| --- | --- | --- | --- |
| `org.csv` | `org_path` | **更新** `name` / `org_type` / `parent_id` / `status` / `remark`（变更父节点视为组织调整，须走 §7 前置清单） | 新增节点，并重算 `path` / `depth` |
| `user.csv` | `account` | **更新** `employee_no` / `name` / `phone` / `email` / `company_id` / `org_id` / `status` / `remark`；`password_hash` **不覆盖**（防重置在职人员口令） | 新增用户，生成随机初始口令 |
| `org_leader.csv` | `(org_path, user_account, leader_type, business_line)` → `uk_org_leader(org_id,user_id,leader_type,category)` | 更新 `sort_no` / `remark` | 新增绑定 |
| `user_position.csv` | `(user_account, org_path)` → `uk_user_org(user_id,org_id)` | 更新 `position` / `is_primary` / `remark` | 新增任职 |
| `user_role.csv` | `(user_account, role_code, scope_org_path)` → `uk_sys_user_role(user_id,role_id,scope_org_key)`（`scope_org_key = IFNULL(scope_org_id,0)`） | 更新 `remark` | 新增角色分配 |

**幂等要求（AC 附加验证）**：同一份文件**连续导入两次**，第二次必须满足
(a) 数据行数与内容不变（除 `updated_at`）；
(b) `sys_log` 中第二次记录为「无变更（0 新增 / 0 更新 / N 跳过）」；
(c) 不产生重复的组织节点、账号、负责人绑定、岗位记录、角色分配。

**不删除原则（T-10 已定稿）**：导入**不做物理删除**，也**不采用**「文件中缺失 = 停用」的同步语义。模板中缺失的既有记录（例如某员工的新文件里整行被删掉）**保持不变**；需要停用的，用 `status=停用`（组织）或 `status=离职`（人员）显式表达。若确需"文件即真相"，另行输出差异报告（列出"库中有、文件无"的记录）由管理员逐条决策，**不静默停用**。

### 6.2 dry-run（仅校验不导入）

| 项目 | 规则 |
| --- | --- |
| 触发 | 后台勾选「仅校验」，或接口参数 `dryRun=true`（建议 API：`oa.admin.bulk.{validate,import}`，见 `dev-plan-v0.3.md` 1.8） |
| 执行内容 | §4 全部文件内规则 + `E-ORG-010`（在途单据）、`E-USER-009`（待办）、`E-USER-013`（账号姓名冲突） + §7 受影响在途单据清单 + §8 拦截统计 |
| 不执行内容 | **不写任何业务表**（`sys_org` / `sys_user` / `sys_org_leader` / `sys_user_position`）；不生成初始口令 |
| 产出 | 与正式导入同结构的校验报告（`dryRun: true`）；含「预计新增 / 预计更新 / 预计跳过」行数；并写 `sys_log`（`action=import_dry_run`）留痕 |

### 6.3 回滚策略

| 层次 | 手段 | 说明 |
| --- | --- | --- |
| **首选：事务回滚** | 单事务包裹四张表的写入，任一失败 `ROLLBACK` | 保证「全成功或全不变」；这是一期的主策略 |
| **二次：批次撤销** | 记录导入批次号（`sys_log.target_id`），提供「按批次撤销」入口 | 撤销 = 用 `before_json` 反向 upsert；仅对**该批次之后未被审批流程引用**的记录安全 |
| **三次：数据库级 + 禁止删除** | 导入前全量备份（REQ-NFR-008：RPO ≤24h、RTO ≤4h）；**禁止**物理删除已导入的人员/组织（违反 REQ-ADMIN-006），一律以 `status` 停用/离职表达 | 兜底手段 |

### 6.4 并发与锁定

- 同一租户在同一时刻**只允许一个导入任务**（分布式锁，锁键 `oa:import:org_user`），并发导入直接拒绝并提示「已有导入任务进行中」；导入期间对 `sys_org` / `sys_user` 的后台单条写操作建议串行化，避免业务键冲突。

## 7. 批量调整的前置提示：受影响在途单据清单

### 7.1 触发条件（PRD 5.5：批量调整由系统管理员导入，导入前给出受影响在途单据清单，确认后方可执行）

触发清单的场景（同时满足「影响审批人解析或数据域」与「涉及在途单据」）：

| # | 场景 | 判定 |
| --- | --- | --- |
| 1 | 组织 `parent_path` 变更（节点被移动，如公司重组） | `sys_org.parent_id` 发生变化 |
| 2 | 组织 `status` 由 `启用` → `停用` | 停用节点下仍有在途单据 |
| 3 | 人员 `company_path` / `dept_path` 变更（跨公司/跨部门调动） | 该员工是某在途单据的发起人或待办审批人 |
| 4 | 人员 `status` 由 `在职` → `离职` | 该员工名下有未处理待办（§8.1） |
| 5 | 组织负责人新增/更换（`org_leader` 变更） | 变更节点是某在途单据的当前/后续节点候选来源 |

> **重要口径（PRD 5.4）**：审批人在**流程发起时一次性快照**，调岗/离职/组织调整**不改变在途单据**。所以本清单的作用**不是自动改派**，而是让管理员**知情并确认**「这批在途单据将继续由原快照审批人处理；若该人无法处理，需事后改派或转办」。

### 7.2 清单字段

| 字段 | 说明 | 来源 |
| --- | --- | --- |
| `单号` | 单据编号（流程实例编号） | `flow_instance.id` / 单号规则 |
| `单据类型` | 事项审批单 / 资金审批单 / 合同审批单 / 印鉴证照审批单 | `flow_instance.form_type` → `matter`/`fund`/`contract`/`seal` |
| `发起人` | 姓名 + 账号 | `sys_user.name` / `account` |
| `当前节点` | 当前所处节点（①–⑦，含节点名） | `flow_node_instance.node_code` / `node_name` |
| `当前审批人` | 快照中的当前任务处理人 | `flow_task.assignee_id`（`status='pending'`） |
| 受影响原因 | 枚举文案：`组织停用` / `组织移动` / `发起人调岗` / `发起人离职` / `审批人调岗` / `审批人离职` / `负责人变更` | 由导入程序按差异比对生成 |
| `影响程度` | `高`（当前/后续审批人失效，需改派） / `中`（发起人归属变更，数据域可见性变化） / `低`（仅历史留痕） | 判定规则 |

### 7.3 清单样例（人工可读版）

| 单号 | 单据类型 | 发起人 | 当前节点 | 当前审批人 | 受影响原因 | 影响程度 | 处理建议 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ZJ-2026-000118 | 资金审批单 | 张三（zhangsan01） | ③分公司分管领导 | 王五（wangwu03） | 审批人离职 | 高 | 由系统管理员改派（AC-52） |
| ZJ-2026-000131 | 事项审批单 | 孙七（sunqi0005） | ②财务部复核 | 赵六（zhaoliu04） | 发起人离职 | 中 | 待单据回到发起人后清理；快照审批人不变（PRD 5.4） |

### 7.4 确认与留痕

1. 清单必须在**执行导入前**展示，并要求管理员**逐条勾选确认**或整体确认；确认动作写 `sys_log`（`action=bulk_change_confirm`，`before_json` 含清单快照）。
2. **清单为空或全部确认后**才允许提交导入；存在「影响程度 = 高」且未确认的条目 → 阻断（未处理的高影响条目会导致单据卡死，属 PRD 5.5 明令要防的情形）。W12 之前（阶段 2b 流程引擎未上线）无在途单据，清单恒为空，能力随 `dev-plan-v0.3.md` 1.8「W12 后启用」生效。

## 8. 离职与停用的补偿控制

> 这两条是「审批人快照」策略的**必要补偿控制**：快照保证在途单据不被组织变更影响，代价是离职/停用会让单据永久卡死。PRD 5.5 因此要求逐条拦截。

### 8.1 离职拦截（PRD 5.5 + AC-12）

| 项目 | 规则 |
| --- | --- |
| 触发 | `user.csv` 某行 `status=离职`，且该 `account` 在库中已存在/新增 |
| 检查 | `flow_task` 中 `assignee_id = 该用户` 且 `status='pending'` 的任务数；以及 `flow_node_instance.status='active'` 且候选人为该用户的活动节点数 |
| 结果 | **数量 > 0 → 拒绝整批导入**（error `E-USER-009`），并在报告中给出**待办数量与单号列表** |
| 提示文案 | 「离职前必须清空名下待办：`{name}`（`{account}`）名下仍有 **{n}** 条未处理待办，请先转办或由系统管理员改派；涉及单号：{单号列表}」 |
| 放行条件 | 该员工待办数 = 0（全部转办/改派/办结）后方可导入离职 |
| 依据 | PRD 5.5「员工离职：离职前必须处理完名下全部待办；系统提示未处理任务数量，强制要求先转办或改派」；AC-12 |

**拦截报告样例**：

| 行号 | 列 | 值 | 错误码 | 待办数量 | 涉及单号 | 提示文案 |
| --- | --- | --- | --- | --- | --- | --- |
| 6 | `status` | `离职` | `E-USER-009` | 2 | ZJ-2026-000118、ZJ-2026-000131 | 离职前必须清空名下待办：孙七（sunqi0005）名下仍有 2 条未处理待办，请先转办或改派 |

### 8.2 组织停用拦截（PRD 5.5）

| 项目 | 规则 |
| --- | --- |
| 触发 | `org.csv` 某行 `status=停用` |
| 检查 | 该组织节点（含其整棵子树，按 `sys_org.path` 前缀匹配）下的在途单据数：`flow_instance.status='approving'` 且 `current_dept_id` / 发起人归属落在该子树内 |
| 结果 | **数量 > 0 → 拒绝整批导入**（error `E-ORG-010`），报告给出在途单据**数量与单号列表** |
| 附加检查 | 该子树下仍有 `在职` 人员 → 警告 `W-ORG-017`（不阻断，但须在报告中提示先转岗）；停用后该节点不可作为发起者的归属节点 |
| 提示文案 | 「组织停用前必须清空在途单据：`{org_path}` 及其子树下仍有 **{n}** 张在途单据，请先办结或流转处理；涉及单号：{单号列表}」 |
| 依据 | PRD 5.5「组织节点停用：停用前必须处理完该节点全部在途单据；停用后不可作为发起者的归属节点」 |

**拦截报告样例**：

| 行号 | 列 | 值 | 错误码 | 在途数量 | 涉及单号 | 提示文案 |
| --- | --- | --- | --- | --- | --- | --- |
| 4 | `status` | `停用` | `E-ORG-010` | 3 | ZJ-2026-000118、ZJ-2026-000123、ZJ-2026-000131 | 组织停用前必须清空在途单据：集团/公司A 及其子树下仍有 3 张在途单据 |

### 8.3 与快照策略的关系（为什么不能"自动改派"）

| 做法 | 一期是否采用 | 理由 |
| --- | --- | --- |
| 自动改派在途单据给新负责人 | **否** | PRD 5.4 明确定稿：在途单据**一律不变**，仍由快照审批人处理 |
| 管理员改派兜底 / 转办 | **是（事后）** | 仅系统管理员可改派（必填原因，轨迹与审计留痕，AC-52）；审批人本人可转办（`flow_task.status='transferred'`） |

## 9. 导出

### 9.1 导出列与模板完全一致

导出的 CSV **列名、顺序、取值口径必须与 §3 的五个模板一致**，以保证「导出 → 修改 → 再导入」的**往返编辑**（round-trip）闭环：

| 导出文件 | 列 | 与导入模板的差异 |
| --- | --- | --- |
| `org.csv` | `org_path,org_name,org_type,parent_path,status,remark` | 无差异；`remark` 取自 `sys_org.remark` |
| `user.csv` | `account,employee_no,name,phone,email,company_path,dept_path,status,remark` | 无差异；`phone` **不脱敏**（主数据导出的**显式例外**，见 §9.2.1） |
| `org_leader.csv` | `org_path,user_account,leader_type,sort,business_line,remark` | 无差异 |
| `user_position.csv` | `user_account,org_path,post_name,is_primary,remark` | 无差异 |
| `user_role.csv` | `user_account,role_code,scope_org_path,remark` | 无差异（`remark` 取自 `sys_user_role.remark`） |

**往返约束**：导出文件**必须能被本文档的校验器直接通过**（`node tools/check-import-csv.js <导出目录>` 退出码 0）；「导出全量 → 立即 dry-run 导入 → 预期 0 error、0 新增、0 更新」即幂等自检，也是 AC-57 的**反向验收**（导出能全覆盖导入所需字段，说明数据模型未丢信息）。若导出结果为部分列，则不支持往返。

### 9.2 导出权限

| 项目 | 口径 |
| --- | --- |
| 主数据（组织 / 人员 / 负责人 / 岗位 / 角色分配）导出 | **仅系统管理员**可导出（REQ-ADMIN-001「用户与组织管理」的兜底角色）；导出内容与 §9.1 的模板列完全一致 |
| 单据与金额类导出 | **按 PRD 5.3 执行**：「合同金额、资金金额对非财务类角色只读展示，**不可导出**；**导出功能仅系统管理员与财务角色可用**」 |
| 口径说明（T-11 已定稿） | 两类导出**分组治理、互不覆盖**：主数据看「是否系统管理员」，单据/金额看「是否系统管理员或财务角色」。`enums.md` **S-12** 记录的差异（PRD 5.3/9.1 曾写「导出仅系统管理员」，V0.4 更新为「系统管理员与财务角色」）按本口径收敛 |
| 留痕与脱敏 | 每次导出写 `sys_log`（`action=export`、`target_type=org_import`、导出人、IP、行数、文件哈希）；非系统管理员不可导出主数据；系统管理员导出主数据时 `phone` **不脱敏**（见下方「§9.2.1 显式例外」） |

#### 9.2.1 往返约束的**显式例外**：主数据导出的 `phone` 不脱敏（阶段 1 收口定稿）

**例外内容**：主数据（组织 / 人员 / 负责人 / 岗位 / 角色分配）导出中，**`user.csv` 的 `phone` 列输出完整明文手机号，不做 `138****8888` 脱敏**。

**为什么必须是例外（而不是可选行为）**：`phone` 在 `user.csv` 中是**必填列**且受 §4.3 `E-USER-003`（11 位大陆手机号格式 `^1[3-9]\d{9}$`）约束。若导出物写脱敏值，则 §9.1 的往返闭环（导出 → 修改 → 再导入）**必然失败**：再导入时每一行都会报 `E-USER-003`（`138****8888` 不是合法手机号），该文件不可能被校验器直接通过，AC-57 的「导出全覆盖导入字段」反向验收同时失效。因此「主数据导出不脱敏」是**往返导入可还原**的必要条件，属于 §9.1 往返约束的**唯一例外**，且仅限主数据导出。

**三条限定（缺一不可）**：

| # | 限定 | 落地形态 |
| --- | --- | --- |
| ① | **仅系统管理员可取**：`GET /api/v1/identity/users/export` 要求角色 `admin`，权限码 `admin:user:export`；其他角色一律 **403 / 40305 `EXPORT_DENIED`** | 服务层 `UserService#exportCsv` 判定 `ForceReasonPolicy.ADMIN_ROLE`；越权矩阵测试断言 5 个非管理员角色全部 403 |
| ② | **导出行为必写审计日志**（REQ-AUTH-003）：每次导出落 `sys_log`（`action=export`、导出人、IP、行数） | `BulkImportController` 三条模板导出与 `UserController/OrgController` 主数据导出均标 `@Audited(action="export", ...)` |
| ③ | **脱敏仍由唯一实现 `PhoneVisibilityService` 负责，导出走其 `exportPlain` 且内含二次鉴权** | `UserService#exportCsv` 调 `phoneVisibility.exportPlain(principal, cipher)`；该方法**先解密**（库中是 AES-256-GCM 密文，直接写库值会把密文写进 CSV）并**内部再次校验系统管理员**——调用方无法绕过，也不会出现「密文进 CSV」或「第二份脱敏实现」两种漂移 |

**与 PRD §5.3「通讯录他人手机号显示 `138****8888`」不矛盾**：两者约束的是**不同路径**——PRD §5.3 约束的是**展示路径**（通讯录 / 人员列表 / 单据详情 / H5 等界面渲染，必须脱敏，唯一实现仍是 `PhoneVisibilityService#display`），本文档 §9.2.1 约束的是**数据维护导出路径**（导出物是「拿去修改后再导入」的维护载体，不是展示载体）。即：**同一个手机号，界面上一律 `138****8888`，系统管理员导出的维护文件里是完整值**；两条口径由同一个服务类的两个方法分别承载（`display` / `exportPlain`），不存在第二份判定逻辑。

> 反向说明：**不存在**「导出到界面/接口响应里出现明文」的旁路——脱敏与否只在 `PhoneVisibilityService` 内按「调用人是否系统管理员 + 用途是展示还是导出」决定；任何新增展示入口都必须走 `display`。

### 9.3 金额导出口径（按 PRD §5.3 / AC-18）

| 项目 | 口径 |
| --- | --- |
| 金额对**非财务类角色** | **不可导出**：这类角色在**导出入口**即被 **403 `EXPORT_DENIED`** 拒绝，不存在「导出物里恰好少一列」这种弱保证；此外审计日志 JSON（`before_json` / `after_json`）内的金额键**无条件递归剔除**（`redactAmountKeys`，解析失败则整列占位 fail-closed） |
| **导出功能本身** | **仅系统管理员与财务角色可用**（PRD §5.3 / AC-18 原文）；其他角色调用任一导出入口 → **403** |
| 实现开关 | `oa.authz.export.amount-enabled`（布尔，**默认 `true`**）：置 `false` 时**一律剔除**金额列（连系统管理员/财务角色也拿不到，用于临时收紧）；置 `true` 时金额随系统管理员与财务角色的导出物下发 |
| 闸门位置 | **角色门禁在导出入口**（控制器/服务层的导出授权点），不通过则 **403 `EXPORT_DENIED` / `EXPORT_FIELD_DENIED`**；不得把闸门做成「把金额从所有人（含财务角色）的导出物里一律剔除」——那会让财务角色也导不出，与 PRD §5.3 相悖 |
| 预检接口 | `POST /api/v1/authz/export-check`：按角色与目标（`target`）返回 `effectiveColumns` / `amountExported` / `excludedFields`，供前端在导出前**预检生效列**；伪造列白名单外的字段（如 `fields:["biz_no","not_a_column"]`）→ **403 / 40307 `EXPORT_FIELD_DENIED`**（列白名单在服务端裁决，界面无法绕过） |
| 审计留痕 | 金额导出同样写 `sys_log`（REQ-AUTH-003），与 §9.2 主数据导出一致 |
| 与 §9.2.1 的关系 | 两条口径**互不覆盖**：主数据看「是否系统管理员」（`phone` 例外见 §9.2.1），单据/金额看「是否系统管理员或财务角色」（金额列对非财务角色不可达） |

## 10. 裁定结论

### 10.1 已确认项（本轮定稿）

| 编号 | 事项 | 定稿结论 | 落地位置 |
| --- | --- | --- | --- |
| T-01 | 组织业务编码 `sys_org.org_code` | **不新增**。以 `org_path` 作为组织的唯一业务键；导入程序据此解析 `parent_id` / `path` / `depth`，不改 `data-model.md` | §3.2、§3.7、§6.1 |
| T-02 | 人员工号列 | **原提法作废**：`sys_user.employee_no` 在 DDL 中**已存在**（`VARCHAR(32) NULL COMMENT '工号（水印使用）'`），无需新增 `user_no`；处置改为把 `employee_no` **纳入 `user.csv` 且定为必填 + 唯一** | §3.3、§3.7、§4.3、§9.1 |
| T-03 | 初始口令生成与下发 | **随机口令 + 线下分发**：管理员批量生成随机初始口令（≥8 位、含字母与数字，符合 REQ-NFR-005）→ **导出加密清单线下分发**（不通过邮件明文发送）→ 员工**首次登录强制改密** → 连续失败 **5 次锁定 15 分钟** | §3.3、§6.2 |
| T-04 | `phone` 为空 | **必填**（校验器 `E-USER-003` 阻断）；手机号是账号安全链路的一环，不启用「无手机号」降级通道 | §3.3、§4.3 |
| T-05 | 是否开放 `停用`（`disabled`） | **不开放**：`user.csv` 只接受 `在职` / `离职`；停用属临时措施，由后台单条操作，避免批量误停用 | §3.3、§4.3 |
| T-06 | 业务线取值 | **直接复用事项类别五值中文标签**：`经营→business` / `经济→economy` / `行政→admin` / `人力→hr` / `投资→invest`，落 `sys_org_leader.category`；不新增枚举。口径：**财务分管领导在系统中等同于「经济」类分管领导**（Q10 后五个事项类别统一归口财务部）。示例取值由「财务」改为「**经济**」 | §3.4、§3.7、§4.4 |
| **T-07** | `remark` 列 | **已确认：新增 `remark` 列（DDL 已落地）**。`sys_org` / `sys_user` / `sys_org_leader` / `sys_user_position` 四表均已加 `remark VARCHAR(255) NULL`（注释「备注（导入模板 remark 列落此处）」），模板 `remark` 列**直接落库**，长度校验统一 ≤255 | §3.2–§3.5、§3.7、§6.1、§9.1 |
| T-08 | `sort_no` / `duty_title` / 生效区间 / 组织排序 | **本期不开放**：`sys_org.sort_no`、`sys_org_leader.duty_title` / `effective_from` / `effective_to` 走缺省值（0 / NULL）；二期按需补齐 | §3.2、§3.4 |
| T-09 | 正职唯一口径 | **按 `org_path` + `business_line` 分组**，每组至多 1 个正职（`business_line` 留空视为同一组）；副职不限 | §4.4、§4.7 |
| T-10 | 「文件中缺失 = 停用」 | **不采用**：导入只增改不删除；若确需"文件即真相"，另出差异报告由管理员逐条决策，不静默停用 | §6.1 |
| T-11 | 导出权限 | **分组治理**：主数据（组织 / 人员 / 负责人 / 岗位 / 角色分配）导出**仅系统管理员**；**单据与金额类按 PRD 5.3**（系统管理员 + 财务角色）；导出行为一律写审计日志 | §9.2 |
| T-12 | 一人多岗的数据域 | **按主归属判定**：`company_path` / `dept_path` 表示主归属，跨公司兼岗仅写 `user_position.csv`，不合并数据域 | §3.5、§4.5 |
| T-13 | 交付形态 | **CSV（UTF-8 BOM）为权威格式**，可一键另存 `.xlsx` 供业务填报；校验器只解析 CSV | §3.1 |
| **T-14** | 角色分配模板 | **已确认：本阶段交付** `user_role.csv`（`user_account` / `role_code` / `scope_org_path` / `remark`），纳入导入第 ⑤ 步与校验器 `requiredFiles`；`sys_role` 仍由后台维护，模板只做「把人挂到已有角色上」 | §2.1–§2.3、§3.6、§4.6、§9.1、§11.1 |
| **T-15** | `sys_user_role.remark` | **已确认并落地**：`sys_user_role` 已补 `remark VARCHAR(255) NULL`（DDL 已改）；同时把唯一键由 `(user_id, role_id, scope_org_id)` 加固为 `(user_id, role_id, scope_org_key)`，其中 `scope_org_key = IFNULL(scope_org_id, 0)` STORED 生成列，**解决了「可空列导致唯一键对 NULL 不去重、同一角色可重复分配」的完整性漏洞** | §3.6、§3.7、§4.6、§6.1、§9.1 |
| **T-16** | 角色码权威集 | **已确认：以 `data-model.md` 3.1 `sys_role.code` 列注释为唯一权威源**（9 个码：`admin` / `company_admin` / `employee` / `dept_leader` / `branch_leader` / `subsidiary_gm` / `finance_owner` / `group_leader` / `chairman`）。模板示例、校验器默认白名单与 §2.3 角色集表**已全部对齐该集合**；仍保留 `IMPORT_ROLE_CODES` 环境变量覆盖能力（实施时角色码若调整，以 `sys_role` 初始化脚本为准） | §2.3、§3.6、§3.7 |

### 10.2 仍待确认项

**无。** T-01 ~ T-16 全部为「已确认」；本文档与模板、校验器、DDL 三方口径一致。后续如有业务调整，按 §11.2 变更记录追加版本。

## 11. 附录

### 11.1 校验器用法与退出码

`node tools/check-import-csv.js oa-deploy/import`（校验交付模板；业务填报文件传任意目录，如 `.\incoming\2026-10-02`）。输出 JSON 到 stdout：

| 退出码 | 含义 | CI 处置 |
| --- | --- | --- |
| `0` | 无 error（可含 warning） | 通过 |
| `1` | 存在 error | 失败，禁止合并/上线 |
| `2` | 用法错误或目录不存在 | 失败，检查命令 |

**覆盖范围**：§4.1–§4.6 中全部文件内可判定规则（五份文件）；**不覆盖**需查库的 `E-ORG-010`、`E-USER-009`、`E-USER-013` 与需业务判断的 `W-ORG-014`（由服务端 dry-run 执行，见 §6.2）。**角色白名单可配置**：`IMPORT_ROLE_CODES=admin,employee,...` 覆盖脚本内置默认值（默认值即 `sys_role.code` 权威集，见 §2.3 / §10.1 T-16），报告的 `spec.roleCodeSource` / `spec.roleCodes` 会回显实际生效的角色集。建议把该校验加入流水线，防止模板表头/编码/示例行被误改。

### 11.2 变更记录

| 版本 | 日期 | 修改说明 |
| --- | --- | --- |
| V1.1 | 2026-10-02 | **业务裁定后修订**：①补 `user.csv` 的 `employee_no` 列（必填 + 唯一，映射 DDL 既有列 `sys_user.employee_no`，水印 REQ-USER-004 / AC-44），校验器同步新增 `E-USER-014`（必填 + 格式）/ `E-USER-015`（唯一）；②`business_line` 改为事项类别五值中文标签（示例「财务」→「经济」），明确「财务分管领导 ≡ 经济类分管领导」；③T-03 定稿为随机口令 + 加密清单线下分发 + 首登改密 + 5 次失败锁 15 分钟；④T-11 定稿为「主数据仅系统管理员 / 单据与金额按 PRD 5.3」分组治理；⑤T-01、T-04、T-05、T-08、T-09、T-10、T-12、T-13 全部按建议定稿，T-02 作废 |
| V1.2 | 2026-10-02 | **收尾修订**：①T-07 定稿——四表 `remark VARCHAR(255) NULL` 已落地，模板 `remark` 列由「仅写 `sys_log`」改为**落库**，新增 `E-LEAD-010` / `E-POS-007` 长度校验，`E-ORG-013` / `E-USER-012` 文案对齐列宽；②T-14 定稿——**新增第五张模板 `user_role.csv`**（角色分配），纳入第 ⑤ 步与校验器 `requiredFiles`，新增 §2.3 角色集白名单（可配置 `IMPORT_ROLE_CODES`）、§3.6 逐列说明、§4.6 校验规则（`E-ROLE-001` ~ `E-ROLE-005`）、§9.1 导出列；③§4.6–§4.8 与 §3.7 章节重编号 |
| V1.3 | 2026-10-02 | **T-15 / T-16 定稿，本文档「仍待确认项」清零**：①T-15——`sys_user_role` 补 `remark VARCHAR(255) NULL`（模板 `remark` 全部落库），并把唯一键加固为 `(user_id, role_id, scope_org_key)`（`scope_org_key = IFNULL(scope_org_id,0)` STORED），修复「可空列使唯一键对 NULL 不去重」的完整性漏洞，§3.6/§3.7/§4.6/§6.1/§9.1 同步；②T-16——**以 `data-model.md` 3.1 `sys_role.code` 为唯一权威角色码集**（9 个码），§2.3 角色集表、`user_role.csv` 示例与校验器默认白名单全部对齐，保留 `IMPORT_ROLE_CODES` 覆盖能力 |
| V1.4 | 2026-10-03 | **阶段 1（1.6/1.7/1.8）实现追平，规格与实现对齐**：①新增数据域越权错误码号段 `E-ORG-020` / `E-USER-020` / `E-LEAD-020` / `E-POS-020` / `E-ROLE-020`（fail-closed **整批拒绝**），写入 §4.2–§4.6（含触发列与提示文案口径）、§4.6 后的统一口径说明、§5.2 码段约定与 §5.3 阶段 A/B；②§9.2.1 **新增显式例外**——主数据导出的 `phone` 不脱敏（往返导入可还原的必要条件），附三条限定（仅系统管理员 `admin:user:export` / 导出写审计 `REQ-AUTH-003` / 脱敏唯一实现 `PhoneVisibilityService#exportPlain` 内含二次鉴权），并说明与 PRD §5.3 展示路径口径不矛盾；③**新增 §9.3 金额导出口径**（按 PRD §5.3 / AC-18：非财务角色不可导出、导出功能仅系统管理员与财务角色、`oa.authz.export.amount-enabled`、角色门禁在导出入口、`POST /authz/export-check` 预检生效列）；④§9.1 `user.csv` 行同步为「`phone` 不脱敏（见 §9.2.1）」；⑤**新增 §11.3 本阶段落地的 HTTP 路由清单**（五类导入 preview/commit、四个导出入口、`/authz/field-policy/*`、`/authz/export-check|export-policy`、`/admin/crypto/*`、`/admin/keys*`） |

> **交叉引用**：行为语义见 [`prd-0.1.md`](prd-0.1.md)（5.1 / 5.3 / 5.4 / 5.5 / REQ-ADMIN-001 / AC-11 / AC-12 / AC-57）；表结构见 [`data-model.md`](data-model.md)（2.1–2.4、3.1–3.2）；枚举见 [`enums.md`](enums.md)（§10.1、§1.1）；排期见 [`dev-plan-v0.3.md`](dev-plan-v0.3.md)（0.6、1.8）；模板与校验器见 [`../oa-deploy/import/`](../oa-deploy/import/) 与 [`../tools/check-import-csv.js`](../tools/check-import-csv.js)。

### 11.3 本阶段落地的 HTTP 路由清单（阶段 1 · 1.6 / 1.7 / 1.8）

> 口径：**本节是「实现追平规格」的对照表**——每一行都是**真实注册并实测通过**的路由（实测证据见 `AuthzMatrixHttpTest`）；表格里的「错误码」指业务错误码（HTTP 状态码见括号）。**未在本表出现的路由视为尚未实现**，不得据本文档假设其存在。

#### 11.3.1 五类批量导入（第 ①②③④⑤ 步，§2.1 顺序不可调换）

| 模板（§3） | 预检（dry-run，不落库） | 执行（单事务，错误零落库） |
| --- | --- | --- |
| ① `org.csv` | `POST /api/v1/identity/orgs/import/preview` | `POST /api/v1/identity/orgs/import` |
| ② `user.csv` | `POST /api/v1/identity/users/import/preview` | `POST /api/v1/identity/users/import` |
| ③ `org_leader.csv` | `POST /api/v1/identity/org-leaders/import/preview` | `POST /api/v1/identity/org-leaders/import` |
| ④ `user_position.csv` | `POST /api/v1/identity/user-positions/import/preview` | `POST /api/v1/identity/user-positions/import` |
| ⑤ `user_role.csv` | `POST /api/v1/identity/user-roles/import/preview` | `POST /api/v1/identity/user-roles/import` |

| 辅助接口 | 说明 |
| --- | --- |
| `POST /api/v1/admin/bulk-import/impact-preview` | 受影响在途单据清单预检（§7，dry-run 不落库；参数 `kind` ∈ 五类） |
| `GET  /api/v1/admin/bulk-import/kinds` | 五类导入元数据（`kind` / `label` / `file` / `columns` / `previewRoute` / `commitRoute`），供前端渲染五步流水线 |

- **入参两种形态**：`multipart/form-data` 的 `file` 字段（浏览器上传），或直接把 CSV 作为请求体（`curl --data-binary @org.csv`，便于脚本化）；文件必须是 **UTF-8 BOM** 的 CSV（§4.1 `E-ENC-001`）。
- **权限**：系统管理员或分公司流程管理员；分公司管理员逐行受数据域约束，越域行 → `E-*-020` **整批拒绝**（fail-closed）。
- **失败码**：存在 error → `400 / 40005 IMPORT_VALIDATION_FAILED`（整批已拒绝）；文件本身不合法 → `400 / 40006 IMPORT_FILE_INVALID`；同一时刻已有导入在进行 → `409 / 40905 IMPORT_IN_PROGRESS`（§6.4 分布式锁）。
- **留痕**：全部走 `@Audited`；commit 的响应体含**仅本次返回**的初始口令，故 `recordAfter=false`（口令绝不进审计日志，T-03）。

#### 11.3.2 导出入口（§9）

| 导出物 | 路由 | 权限（§9.2 / §9.2.1） |
| --- | --- | --- |
| 组织主数据 `org.csv` | `GET /api/v1/identity/orgs/export` | 仅系统管理员 |
| 人员主数据 `user.csv` | `GET /api/v1/identity/users/export` | 仅系统管理员（权限码 `admin:user:export`；`phone` **不脱敏**，§9.2.1） |
| 负责人 `org_leader.csv` | `GET /api/v1/identity/org-leaders/export` | 仅系统管理员 |
| 岗位任职 `user_position.csv` | `GET /api/v1/identity/user-positions/export` | 仅系统管理员 |
| 角色分配 `user_role.csv` | `GET /api/v1/identity/user-roles/export` | 仅系统管理员 |
| 审计日志 `audit-log.csv` | `GET /api/v1/admin/audit-logs/export` | 仅系统管理员（金额键递归剔除，§9.3） |

非授权角色 → **403 / 40305 `EXPORT_DENIED`**（手改 URL 直连接口同样被拒）。

#### 11.3.3 字段级限制与导出策略（阶段 1.6）

| 路由 | 说明 | 关键错误码 |
| --- | --- | --- |
| `GET  /api/v1/authz/field-policy/amount` | 读取金额字段对**当前角色**的读写策略 | — |
| `GET  /api/v1/authz/field-policy/contact` | 读取联系方式脱敏策略（`138****8888` 形态与加密算法口径） | — |
| `POST /api/v1/authz/field-policy/assert-write` | **写闸门**：按「状态白名单 ∧ 角色金额规则」判定本次表单写入是否放行（非财务角色写金额 → 拒绝） | `403 / 40306 AMOUNT_READ_ONLY`、`403 / 40304 FIELD_WRITE_DENIED` |
| `POST /api/v1/authz/export-check` | 导出前预检：返回 `effectiveColumns` / `amountExported` / `excludedFields`（§9.3） | `403 / 40307 EXPORT_FIELD_DENIED` |
| `GET  /api/v1/authz/export-policy` | 读取导出目标的权威列清单与限制 | `403 / 40305 EXPORT_DENIED` |

#### 11.3.4 敏感字段加密与密钥管理（阶段 1.7）

| 路由 | 说明 | 备注 |
| --- | --- | --- |
| `GET  /api/v1/admin/crypto/fields` | 已加密字段与当前脱敏方式（**不返回密钥本体**） | 仅系统管理员 |
| `POST /api/v1/admin/crypto/phone-migrate` | 一次性把库中**历史明文**手机号加密为密文（**幂等**：`migrated=5 → 0`） | 仅系统管理员；密钥缺失时启动期即 fail-fast（`500 / 50003 CRYPTO_KEY_UNAVAILABLE`） |
| `GET  /api/v1/admin/keys` | 密钥状态：活动 `keyId` + 可用 `keyId` 列表（**永不下发密钥本体**） | 仅系统管理员 |
| `POST /api/v1/admin/keys/rotate` | 密钥轮换：把非活动 `keyId` 的密文收敛到活动密钥（幂等） | 仅系统管理员 |

非系统管理员访问 `/api/v1/admin/**` 一律 **403**（越权矩阵测试对 5 个非管理员角色逐一断言）。
