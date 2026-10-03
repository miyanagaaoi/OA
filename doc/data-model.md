# 集团OA审批系统 · 数据模型与 DDL

> 配套文档：本文档是 [`prd-0.1.md`](prd-0.1.md) 第 7 章的完整落地。**DDL 为准**，PRD 7.1 的字段概览如与本文档冲突，以本文档为准。
>
> 版本：V0.4 配套修订 · 状态：评审稿 · 目标数据库：MySQL 8.0（私有化部署）；PostgreSQL 差异见第 9 章

---

## 1. 通用约定

| 项目 | 约定 |
| --- | --- |
| 存储引擎 | InnoDB，`utf8mb4` / `utf8mb4_0900_ai_ci` |
| 时区 | 全部 `DATETIME` 存 UTC，应用层按 `Asia/Shanghai` 展示 |
| 主键 | `BIGINT UNSIGNED AUTO_INCREMENT`，业务编号另设唯一键 |
| 布尔 | `TINYINT(1)`，0/1 |
| 金额 | **`DECIMAL(18,2)`**，禁止 `FLOAT` / `DOUBLE` |
| 枚举 | 用 `VARCHAR(32)` + `CHECK` 约束（便于扩展，不改表结构） |
| 逻辑删除 | 仅"基础数据"（组织、用户、角色、字典、模板）用 `deleted_at`；**审批类数据一律不删除** |
| 审计字段 | `created_at` / `created_by` / `updated_at` / `updated_by`，审批类表只写不改 |
| 索引命名 | `idx_表名_字段`、`uk_表名_字段`、`fk_表名_字段` |
| 字符 | 组织路径 `path` 形如 `/1/12/135/`，便于子树查询 |

**领域划分**：① 身份与组织（6 表）② 权限（6 表）③ 定义（2 表）④ 运行时（5 表）⑤ 签名/附件/消息/审计（6 表）⑥ 业务（1 表）⑦ 会话（1 表）＝ **27 张**。

> 口径说明：②的 6 张含 `sys_dict_item`，该表属**配置/字典**用途，故在 §11 清单的"领域"列记为「配置/字典」；`form_data` 为业务数据表，计⑥；`sys_user_session` 为 V0.4 新增的会话与设备表，计⑦（DDL 见 2.7）。变更前的旧账（"身份与组织 7 / 权限 5 / 流程定义 3 ＝ 26 表"）与本文档不符，以本行为准。

---

## 2. 身份与组织

```sql
-- ============================================================
-- 2.1 组织架构（集团-公司-部门-科室 四级，可扩展）
-- ============================================================
CREATE TABLE sys_org (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id     BIGINT UNSIGNED     NULL COMMENT '上级节点；集团根节点为 NULL',
  org_type      VARCHAR(16)  NOT NULL COMMENT 'group=集团 company=公司 dept=部门 section=科室',
  name          VARCHAR(100) NOT NULL,
  path          VARCHAR(255) NOT NULL COMMENT '祖先路径 /1/12/135/，含自身',
  depth         TINYINT UNSIGNED NOT NULL COMMENT '层级：1集团 2公司 3部门 4科室',
  leader_id     BIGINT UNSIGNED     NULL COMMENT '主负责人（冗余，权威数据在 sys_org_leader）',
  sort_no       INT          NOT NULL DEFAULT 0,
  status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active=启用 disabled=停用',
  remark        VARCHAR(255)     NULL COMMENT '备注（导入模板 remark 列落此处）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by    BIGINT UNSIGNED     NULL,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by    BIGINT UNSIGNED     NULL,
  deleted_at    DATETIME         NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_org_path (path),
  KEY idx_sys_org_parent (parent_id),
  KEY idx_sys_org_type_status (org_type, status),
  CONSTRAINT fk_sys_org_parent FOREIGN KEY (parent_id) REFERENCES sys_org (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织架构（PRD 7.1 概览旧名 type → 本表实际列名 org_type）';

-- ============================================================
-- 2.2 用户
-- ============================================================
CREATE TABLE sys_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  account       VARCHAR(64)  NOT NULL COMMENT '登录账号，唯一',
  name          VARCHAR(50)  NOT NULL,
  employee_no   VARCHAR(32)      NULL COMMENT '工号（水印使用）',
  password_hash VARCHAR(255) NOT NULL COMMENT '加盐哈希，禁止明文',
  phone         VARCHAR(255)     NULL COMMENT '加密存储；展示按角色脱敏',
  email         VARCHAR(128)     NULL,
  org_id        BIGINT UNSIGNED     NULL COMMENT '主归属组织节点',
  company_id    BIGINT UNSIGNED     NULL COMMENT '归属公司（数据域判定用）',
  position      VARCHAR(50)      NULL COMMENT '职务名称',
  status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active=在职 disabled=停用 resigned=离职',
  remark        VARCHAR(255)     NULL COMMENT '备注（导入模板 remark 列落此处）',
  last_login_at DATETIME         NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by    BIGINT UNSIGNED     NULL,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by    BIGINT UNSIGNED     NULL,
  deleted_at    DATETIME         NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_account (account),
  KEY idx_sys_user_org (org_id),
  KEY idx_sys_user_company_status (company_id, status),
  CONSTRAINT fk_sys_user_org FOREIGN KEY (org_id) REFERENCES sys_org (id),
  CONSTRAINT fk_sys_user_company FOREIGN KEY (company_id) REFERENCES sys_org (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户（PRD 7.1 概览旧名 password → 本表实际列名 password_hash；phone 加密存储、按角色脱敏）';

-- ============================================================
-- 2.3 组织负责人（支持多负责人、一人多岗）
-- ============================================================
CREATE TABLE sys_org_leader (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  org_id       BIGINT UNSIGNED NOT NULL,
  user_id      BIGINT UNSIGNED NOT NULL,
  leader_type  VARCHAR(16)  NOT NULL DEFAULT 'primary' COMMENT 'primary=正职 deputy=副职',
  duty_title   VARCHAR(50)      NULL COMMENT '岗位名，如"财务分管领导"',
  category     VARCHAR(32)      NULL COMMENT '事项类别（配置项，五值：business=经营 economy=经济 admin=行政 hr=人力 invest=投资）；按业务线绑定时填，仅用于限定范围，不参与流程路由；旧码 operate 作废并迁移为 business（enums.md §14）',
  sort_no      INT          NOT NULL DEFAULT 0,
  effective_from DATE           NULL,
  effective_to   DATE           NULL,
  remark         VARCHAR(255)       NULL COMMENT '备注（导入模板 remark 列落此处）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by   BIGINT UNSIGNED     NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by   BIGINT UNSIGNED     NULL,
  category_key VARCHAR(32)  GENERATED ALWAYS AS (IFNULL(category, '')) STORED COMMENT '唯一键口径：category 为 NULL 时按空串参与唯一键（MySQL 唯一键对 NULL 不去重，否则同一组织/同一人/同一 leader_type 在 category 为 NULL 时可无限重复插入）。业务语义不变：NULL 与 NULL 视为同一组、非 NULL 仍按值区分',
  PRIMARY KEY (id),
  UNIQUE KEY uk_org_leader (org_id, user_id, leader_type, category_key),
  KEY idx_org_leader_user (user_id),
  KEY idx_org_leader_lookup (org_id, category, leader_type),
  CONSTRAINT fk_org_leader_org  FOREIGN KEY (org_id)  REFERENCES sys_org (id),
  CONSTRAINT fk_org_leader_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织负责人（审批人解析的唯一权威来源）';

-- ============================================================
-- 2.4 岗位任职（一人多岗：一个人可在多个组织节点任职）
-- ============================================================
CREATE TABLE sys_user_position (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  org_id      BIGINT UNSIGNED NOT NULL,
  is_primary  TINYINT(1)   NOT NULL DEFAULT 0,
  position    VARCHAR(50)      NULL,
  remark      VARCHAR(255)     NULL COMMENT '备注（导入模板 remark 列落此处）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_org (user_id, org_id),
  KEY idx_user_position_org (org_id),
  CONSTRAINT fk_user_position_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_user_position_org  FOREIGN KEY (org_id)  REFERENCES sys_org (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位任职（一人多岗）';

-- ============================================================
-- 2.5 签名预存（个人中心预存签名）
-- ============================================================
CREATE TABLE sys_user_signature (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  sign_image   MEDIUMTEXT   NOT NULL COMMENT 'base64 或对象存储路径',
  is_default   TINYINT(1)   NOT NULL DEFAULT 1,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_signature_user (user_id, is_default),
  CONSTRAINT fk_user_signature_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户预存签名';

-- ============================================================
-- 2.6 登录日志
-- ============================================================
CREATE TABLE sys_login_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED     NULL,
  account     VARCHAR(64)  NOT NULL COMMENT '失败时也记录尝试的账号',
  result      VARCHAR(16)  NOT NULL COMMENT 'success / fail',
  fail_reason VARCHAR(64)      NULL COMMENT 'bad_password / locked / disabled',
  ip          VARCHAR(64)      NULL,
  user_agent  VARCHAR(255)     NULL,
  device_fingerprint VARCHAR(128) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_login_log_user_time (user_id, created_at),
  KEY idx_login_log_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志（保留 1 年）';

-- ============================================================
-- 2.7 用户会话与设备（多设备登录：默认 3 台，超出踢出最早登录的设备）
--     权威：REQ-USER-003 / REQ-NFR-006；在线设备上限为配置项（默认 3）
-- ============================================================
CREATE TABLE sys_user_session (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT UNSIGNED NOT NULL,
  device_fingerprint VARCHAR(128)    NULL COMMENT '设备/浏览器指纹，与签名记录同源',
  ip                 VARCHAR(64)     NULL,
  user_agent         VARCHAR(255)    NULL,
  token_hash         CHAR(64)        NOT NULL COMMENT '会话令牌哈希（只存哈希，禁止存明文令牌）',
  login_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本次登录时间（"踢出最早登录设备"按本列排序）',
  last_active_at     DATETIME        NULL COMMENT '最近活跃时间，访问时自动续期',
  expires_at         DATETIME        NOT NULL COMMENT '过期时间：未勾选"记住我"=会话级；勾选=7 天（REQ-USER-002）',
  revoked_at         DATETIME        NULL COMMENT '失效时间；NULL=有效。同一 user_id 下有效会话数不得超过配置上限（默认 3）',
  revoked_reason     VARCHAR(32)     NULL COMMENT 'logout=主动登出 kicked=超设备上限被踢 expired=过期 password_changed=改密失效 disabled=停用',
  created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_session_token (token_hash),
  KEY idx_user_session_user (user_id, revoked_at) COMMENT '判定在线设备数与踢出最早设备的主索引',
  KEY idx_user_session_expire (expires_at) COMMENT '过期会话清理扫描索引',
  CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT chk_user_session_revoked_reason CHECK (revoked_reason IS NULL OR revoked_reason IN ('logout','kicked','expired','password_changed','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话与设备（多设备登录上限默认 3，超出踢出最早登录设备；REQ-USER-003）';
```

---

## 3. 权限（RBAC + 数据域）

```sql
-- ============================================================
-- 3.1 角色
-- ============================================================
CREATE TABLE sys_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(32)  NOT NULL COMMENT 'admin=系统管理员 company_admin=分公司流程管理员 employee=普通员工 dept_leader=部门/科室负责人 branch_leader=分公司分管领导 subsidiary_gm=子公司总经理 finance_owner=集团归口（财务部）负责人 group_leader=集团分管领导 chairman=集团董事长',
  name        VARCHAR(50)  NOT NULL,
  role_scope  VARCHAR(16)  NOT NULL COMMENT 'group=集团级 company=公司级',
  data_scope  VARCHAR(16)  NOT NULL DEFAULT 'self'
              COMMENT 'self 本人 | dept 本部门 | company 本公司 | group_all 全集团 | group_category 全集团按归口类别',
  remark      VARCHAR(255)     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by  BIGINT UNSIGNED     NULL,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by  BIGINT UNSIGNED     NULL,
  deleted_at  DATETIME         NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (code),
  CONSTRAINT chk_sys_role_scope CHECK (data_scope IN ('self','dept','company','group_all','group_category'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- ============================================================
-- 3.2 用户角色
-- ============================================================
CREATE TABLE sys_user_role (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  role_id    BIGINT UNSIGNED NOT NULL,
  scope_org_id BIGINT UNSIGNED   NULL COMMENT '该角色生效的组织范围；为空=按角色默认',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT UNSIGNED     NULL,
  remark     VARCHAR(255)     NULL COMMENT '备注（导入模板 remark 列落此处）',
  scope_org_key BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(scope_org_id, 0)) STORED COMMENT '唯一键口径：scope_org_id 为 NULL 时按 0 参与唯一键（MySQL 唯一键对 NULL 不去重，避免同一角色被重复分配）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_role (user_id, role_id, scope_org_key),
  KEY idx_user_role_role (role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- ============================================================
-- 3.2b 角色 × 组织节点（权限树「逐级分配」的组织维度）
--     PRD 5.2：IT 部门在后台为每个角色勾选**可访问的组织节点**与功能菜单，支持逐级分配。
--     与 sys_role_category 的分工：category 限定「业务线 / 事项类别」，本表限定「组织节点」。
--     与 sys_user_role.scope_org_id 的分工：本表是**角色级**可访问范围（授权面）；
--     后者是**某个人**持有该角色时的生效范围（分配面）；二者取交集。
-- ============================================================
CREATE TABLE sys_role_org_node (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_id    BIGINT UNSIGNED NOT NULL,
  org_id     BIGINT UNSIGNED NOT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT UNSIGNED     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_org_node (role_id, org_id),
  KEY idx_role_org_node_org (org_id),
  CONSTRAINT fk_role_org_node_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
  CONSTRAINT fk_role_org_node_org  FOREIGN KEY (org_id)  REFERENCES sys_org (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色可访问的组织节点（权限树组织维度）';

-- ============================================================
-- 3.3 角色 × 类别范围（仅 data_scope = group_category 的角色需要配置）
--     用途已收窄：仅用于集团分管领导按分管业务线限定可见范围；
--     不再用于"限定财务部只看某类别"（财务部数据域口径见 7.2）
--     类别为配置项（五值）、不参与流程路由
-- ============================================================
CREATE TABLE sys_role_category (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_id     BIGINT UNSIGNED NOT NULL,
  category    VARCHAR(32)  NOT NULL COMMENT '事项类别（配置项，五值）：business/economy/admin/hr/invest；不参与流程路由；旧码 operate 作废并迁移为 business',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_category (role_id, category),
  CONSTRAINT fk_role_category_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色 × 类别范围（仅用于集团分管领导按业务线限定；类别不参与路由，财务部数据域见 7.2）';

-- ============================================================
-- 3.4 权限树
-- ============================================================
CREATE TABLE sys_permission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id   BIGINT UNSIGNED     NULL,
  perm_type   VARCHAR(16)  NOT NULL COMMENT 'menu=菜单 button=按钮 api=接口',
  code        VARCHAR(64)  NOT NULL COMMENT '唯一权限码，如 flow:task:approve',
  name        VARCHAR(50)  NOT NULL,
  url         VARCHAR(255)     NULL COMMENT '前端路由或接口路径',
  sort_no     INT          NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_permission_code (code),
  KEY idx_sys_permission_parent (parent_id),
  CONSTRAINT fk_sys_permission_parent FOREIGN KEY (parent_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限树';

-- ============================================================
-- 3.5 角色权限
-- ============================================================
CREATE TABLE sys_role_permission (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_id       BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by    BIGINT UNSIGNED     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permission (role_id, permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
  CONSTRAINT fk_role_permission_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限';

-- ============================================================
-- 3.6 数据字典
-- ============================================================
CREATE TABLE sys_dict_item (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dict_type     VARCHAR(32)  NOT NULL COMMENT '字典类型白名单（V0.4 定稿，8 类）：matter_category/contract_type/seal_type/cert_type/payment_method/group_dept/review_dept_other/return_status；旧值 category→matter_category、pay_method→payment_method、cert_name→cert_type 一律作废（存量迁移见 enums.md §14、种子数据见 doc/dict-seed.md §0.3）',
  item_code     VARCHAR(32)  NOT NULL,
  item_name     VARCHAR(64)  NOT NULL COMMENT '中文名',
  item_name_en  VARCHAR(64)      NULL COMMENT '英文名（打印稿与双语界面使用）',
  sort_no       INT          NOT NULL DEFAULT 0,
  status        VARCHAR(16)  NOT NULL DEFAULT 'active',
  remark        VARCHAR(255)     NULL COMMENT '备注：业务口径、待确认标记（如"待业务确认"）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_type_code (dict_type, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典（枚举选项，新增无需发版；item_name_en/remark 承载 doc/dict-seed.md 的 V0.4 种子数据）';
```

---

## 4. 流程定义

```sql
-- ============================================================
-- 4.1 流程模板（版本管理：已发起实例锁定发起时版本）
-- ============================================================
CREATE TABLE flow_template (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code              VARCHAR(32)  NOT NULL COMMENT 'matter/fund/contract/seal',
  name              VARCHAR(80)  NOT NULL,
  form_type         VARCHAR(32)  NOT NULL COMMENT 'matter/fund/contract/seal',
  version           INT          NOT NULL DEFAULT 1,
  status            VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft=草稿 published=已发布 archived=已归档',
  node_count        INT          NOT NULL DEFAULT 0,
  form_schema_json  JSON             NULL COMMENT '表单字段定义（驱动渲染；见 doc/forms.md）',
  published_at      DATETIME         NULL,
  max_return_count         INT          NULL COMMENT 'Q6 全单回退次数上限（NULL 或 0 = 不限；1..99）。键名与语义见 templates.md §1.7',
  max_supplement_count     INT          NULL COMMENT 'Q6 全单补件次数上限（NULL 或 0 = 不限；1..99）。键名与语义见 templates.md §1.7',
  supplement_deadline_days INT          NULL COMMENT 'Q7 补件时限天数（NULL = 不设时限；1..365；0 与负数一律拒绝，不设时限请留空）',
  supplement_deadline_type VARCHAR(16)  NULL COMMENT 'Q7 补件时限口径：calendar 自然日 / working 工作日（给了天数但未给口径时按 working）',
  on_supplement_timeout    VARCHAR(16)  NULL COMMENT 'Q7 补件超时处理：notify 仅提醒（默认，与 V0.4「超时仅催办」一致）/ auto_pass 自动通过 / auto_return 自动退回',
  withdraw_window   VARCHAR(32)  NULL COMMENT '撤回窗口口径（模板级配置项，2026-10-04 产品裁定）：until_finance_approved = 允许撤回到节点②通过之前（含②审批中，**默认**，REQ-FLOW-009 口径）/ until_finance_started = 仅允许在节点②开始前撤回（②一旦 active/waiting_supplement/returned 即不可撤，AC-16 严格口径）；**NULL = 取默认 until_finance_approved**（历史数据与既有实例不受影响）。键名与语义见 templates.md §1.8；引擎按实例**发起时锁定的模板版本**取该值（AC-09 / templates.md V-02）',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by        BIGINT UNSIGNED     NULL,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by        BIGINT UNSIGNED     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_template (code, version),
  KEY idx_flow_template_status (code, status),
  CONSTRAINT chk_flow_template_gates CHECK (
    (max_return_count         IS NULL OR max_return_count         BETWEEN 0 AND 99)   AND
    (max_supplement_count     IS NULL OR max_supplement_count     BETWEEN 0 AND 99)   AND
    (supplement_deadline_days IS NULL OR supplement_deadline_days BETWEEN 1 AND 365)  AND
    (supplement_deadline_type IS NULL OR supplement_deadline_type IN ('calendar','working')) AND
    (on_supplement_timeout    IS NULL OR on_supplement_timeout    IN ('notify','auto_pass','auto_return')) AND
    (withdraw_window          IS NULL OR withdraw_window          IN ('until_finance_approved','until_finance_started'))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板（按版本累积，不覆盖历史；末 6 列为模板级闸门与撤回窗口配置 —— Q6/Q7 五项种子取 V0.4 默认值 5 / 3 / 3 / working / notify，withdraw_window 种子留 NULL = 取默认 until_finance_approved）';

-- ============================================================
-- 4.2 流程节点定义
-- ============================================================
CREATE TABLE flow_node (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id      BIGINT UNSIGNED NOT NULL,
  seq              INT          NOT NULL COMMENT '节点序号，与 PRD 6.3 的 ①②③… 对应',
  node_code        VARCHAR(32)  NOT NULL COMMENT '主干 7 节点码：dept_leader=①直属部门负责人 finance_review=②财务部复核（＝集团归口，全单只审一次） branch_leader=③分公司分管领导 subsidiary_gm=④子公司总经理 group_leader=⑤集团分管领导 chairman=⑥集团董事长 archive_register=⑦归档登记',
  name             VARCHAR(50)  NOT NULL,
  node_type        VARCHAR(16)  NOT NULL DEFAULT 'approve' COMMENT 'approve=审批 cc=抄送 condition=条件(二期预留) archive=归档登记（⑦）',
  approver_rule    VARCHAR(32)  NOT NULL COMMENT '审批人解析规则：dept_leader=直属部门负责人（本级无配置则逐级上溯） finance_owner=集团财务部负责人 branch_leader=分公司分管领导 subsidiary_gm=子公司总经理 group_leader=集团分管领导 chairman=董事长 designated=指定人员/角色 initiator_pick=发起人自选 collab_dept_leader=协同部门负责人（旧值 coop_dept_leader 已废弃）',
  approver_param   JSON             NULL COMMENT 'designated 时填 {"user_ids":[...]} 或 {"role_code":"..."}',
  decision_mode    VARCHAR(16)      NULL COMMENT 'any=或签 all=会签 sequence=依次；**NULL=不适用**（仅 ⑦ archive_register 归档登记节点：默认「仅登记不审批」，无决议模式与阈值，不计入审批时长与效率统计；可按模板配置为需审批后填 any/all/sequence）',
  pass_threshold   VARCHAR(16)      NULL COMMENT '会签阈值："50%"(百分比) 或 "2"(绝对人数)；**绝对人数优先**；百分比向上取整（66%×3 人=2 人）；NULL=按"过半"（需通过人数 = 向下取整(候选人数/2)+1）',
  sign_policy      VARCHAR(16)  NOT NULL DEFAULT 'optional' COMMENT 'required=强制 optional=可选 none=不签名',
  timeout_hours    INT              NULL COMMENT '超时时长（仅催办，不自动跳过、不自动升级）；NULL=不启用，最小 24',
  timeout_cc_superior TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '超时催办时是否抄送审批人上级（REQ-FLOW-007）',
  allow_add_sign   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否允许加签',
  allow_jump       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许自由跳转（默认关闭）',
  allow_route      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许流转/回退（集团层节点开启）',
  skip_condition   JSON             NULL COMMENT '跳过条件，如 {"field":"involve_cost","op":"eq","value":false}',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_node_seq (template_id, seq),
  CONSTRAINT fk_flow_node_template FOREIGN KEY (template_id) REFERENCES flow_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点定义';

-- ============================================================
-- 4.3 表单数据
-- ============================================================
CREATE TABLE form_data (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  form_type            VARCHAR(32)  NOT NULL COMMENT 'matter/fund/contract/seal',
  biz_no               VARCHAR(32)  NOT NULL COMMENT '业务单号 OA-YYYY-NNNNNN',
  fields_json          JSON         NOT NULL COMMENT '字段值；键名见 doc/forms.md。库级仅存约束：金额按 DECIMAL(18,2) 口径以字符串存储；**金额上限与格式白名单由应用层强制**（doc/forms.md 1.5）',
  payee_account_cipher VARBINARY(255)   NULL COMMENT '收款账号（表单键 payee_account）的密文；该字段**单独加密存储、不进 fields_json**，列表与详情默认脱敏（****1234），仅财务角色与系统管理员可见完整值（REQ-AUTH-003）',
  schema_version       INT          NOT NULL DEFAULT 1 COMMENT '提交时的表单模板版本（快照，防止模板变更影响在途单据）',
  creator_id           BIGINT UNSIGNED NOT NULL,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_form_data_biz_no (biz_no),
  KEY idx_form_data_creator (creator_id, created_at),
  KEY idx_form_data_type (form_type, created_at),
  CONSTRAINT fk_form_data_creator FOREIGN KEY (creator_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单数据（JSON 承载四类单据字段；payee_account 单独密文落列 payee_account_cipher，不进 fields_json；金额上限由应用层强制；金额字段仅系统管理员与财务角色可导出，REQ-AUTH-003）';
```

### 4.4 资金审批单新增字段的存储约定（Q8 / Q9）

「计划类别」与「付款归属」**不新增数据库列**，按 `fields_json` 的键存储（与其余表单字段一致）。一期**只写不读**——流程引擎、数据域过滤、超时规则一律不得引用这两个键。

```jsonc
// form_data.fields_json（form_type = fund）示例
{
  "title": "8 月供热管网维护款支付",
  "category": "economy",             // 事项分类：配置项（含 invest）；不参与路由
  "plan_category": true,             // Q8 计划类别：**布尔 checkbox**（true=计划内）—— 一期仅存储，不是字典项
  "payment_belong": true,            // Q9 付款归属：**布尔 checkbox**（true=本月度，默认勾选）—— 一期仅存储，不是字典项
  "amount": "1250000.00",            // 金额以字符串存储，禁止浮点
  "payee": "某某市政工程有限公司",
  "pay_method": "transfer",
  "pay_date": "2026-07-15",
  "contract_ref": "OA-2026-000420",
  "urgent": false
}
```

**校验规则**（应用层，写时执行）：

| 键 | 取值 | 默认 | 说明 |
| --- | --- | --- | --- |
| `plan_category` | `true` / `false`（布尔 checkbox） | `true` | 非必填；缺省时按默认值补全后落库；**不是字典项、不写入 options** |
| `payment_belong` | `true` / `false`（布尔 checkbox） | `true` | 非必填；**默认勾选本月度**；**不是字典项、不写入 options** |

> **查询与索引**：一期如需按这两个字段筛选，直接走 `fields_json` 的 JSON 路径查询即可，无需建索引。**二期上线计划管理模块时再评估是否提升为独立列并加索引**（届时需对历史数据做一次回填）。

---

## 5. 流程运行时（核心）

```sql
-- ============================================================
-- 5.1 流程实例
-- ============================================================
CREATE TABLE flow_instance (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  biz_no               VARCHAR(32)  NOT NULL COMMENT '业务单号，与 form_data.biz_no 一致',
  template_id          BIGINT UNSIGNED NOT NULL,
  template_version     INT          NOT NULL COMMENT '发起时锁定的模板版本（REQ-FLOW-006）',
  form_data_id         BIGINT UNSIGNED NOT NULL,
  form_type            VARCHAR(32)  NOT NULL,
  category             VARCHAR(32)      NULL COMMENT '事项分类（快照，不可改判）：business/economy/admin/hr/invest；配置项，不参与路由；旧码 operate 作废并迁移为 business（enums.md §14）。**允许为空（2026-10-04 收敛）**：草稿只是填写中的内容，事项单的类别由用户在表单里选，因此必须能在**选定之前**保存草稿（否则「填类别才能存草稿、存草稿才能填类别」形成鸡生蛋）；空类别在**提交发起**时被预检闸门拦下（⑤集团分管领导按类别解析 → 40007「请在发起时确定事项类别」）——「不允许带着空类别进入审批流」由提交闸门保证，不再由本列的 NOT NULL 保证',
  initiator_id         BIGINT UNSIGNED NOT NULL,
  initiator_org_id     BIGINT UNSIGNED NOT NULL COMMENT '发起人组织快照',
  initiator_company_id BIGINT UNSIGNED NOT NULL COMMENT '发起人公司快照（数据域判定）',
  initiator_org_path   VARCHAR(255) NOT NULL COMMENT '发起时组织路径快照',
  approver_snapshot_json JSON       NOT NULL COMMENT '审批人快照，结构见 7.1（REQ-FLOW-011）',
  status               VARCHAR(24)  NOT NULL DEFAULT 'draft'
                       COMMENT 'draft=草稿 approving=审批中 approved=已通过 rejected=已驳回 withdrawn=已撤回（**瞬时态**：撤回写入轨迹与审计后立即回到 draft，不长期驻留） terminated=已终止；**列宽 24**（enums.md §1.1 允许的 16/24/32 之一）：值域内 terminated/withdrawn/approving 等最长 10 字符，留足后续扩展（原 VARCHAR(16) 与 CHECK 值域不冲突，此处仅为与同族列口径一致）',
  sub_status           VARCHAR(24)      NULL COMMENT '子状态：NULL / pending_supplement=待补件（值域与 doc/enums.md §4 一致）；**列宽必须 ≥18**：pending_supplement 共 18 字符，原 VARCHAR(16) 会在严格模式下报 Data too long，使待补件子状态无法落库（2026-10-03 运行期实测发现）',
  current_node_seq     INT              NULL COMMENT '当前节点序号',
  current_dept_id      BIGINT UNSIGNED NULL COMMENT '集团层当前承接部门（流转后变化）',
  owner_dept_id        BIGINT UNSIGNED NULL COMMENT '归口部门，恒为集团财务部，用于统计与审计（REQ-FLOW-001）',
  routing_seq          INT          NOT NULL DEFAULT 0 COMMENT '当前流转序号',
  routing_count        INT          NOT NULL DEFAULT 0 COMMENT '流转+回退累计次数（上限 5，REQ-FLOW-024）；只计 route 与 rollback，**不含 back_home**（REQ-FLOW-022）',
  supplement_count     INT          NOT NULL DEFAULT 0 COMMENT '补件累计次数（上限 3）',
  submitted_at         DATETIME         NULL COMMENT '首次提交时间',
  finished_at          DATETIME         NULL,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_instance_biz_no (biz_no),
  KEY idx_instance_status (status, sub_status),
  KEY idx_instance_initiator (initiator_id, created_at),
  KEY idx_instance_company (initiator_company_id, status, created_at),
  KEY idx_instance_category (category, status, created_at),
  KEY idx_instance_current_dept (current_dept_id, status),
  CONSTRAINT fk_instance_template  FOREIGN KEY (template_id)  REFERENCES flow_template (id),
  CONSTRAINT fk_instance_form      FOREIGN KEY (form_data_id) REFERENCES form_data (id),
  CONSTRAINT fk_instance_initiator FOREIGN KEY (initiator_id) REFERENCES sys_user (id),
  CONSTRAINT chk_instance_status CHECK (status IN ('draft','approving','approved','rejected','withdrawn','terminated')),
  CONSTRAINT chk_instance_sub_status CHECK (sub_status IS NULL OR sub_status IN ('pending_supplement'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例';

-- ============================================================
-- 5.2 节点实例（运行时快照：一节点一条，会签的多人任务在 flow_task）
-- ============================================================
CREATE TABLE flow_node_instance (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id          BIGINT UNSIGNED NOT NULL,
  node_seq             INT          NOT NULL,
  node_code            VARCHAR(32)  NOT NULL,
  node_name            VARCHAR(50)  NOT NULL,
  node_key             VARCHAR(64)  NOT NULL COMMENT '唯一键口径（写入时由引擎计算）：concat(node_seq,":",node_code,":",IFNULL(dept_id,0))；MySQL 唯一键对 NULL 不去重，故用本列替代裸 dept_id 参与唯一键（见 8.2）',
  dept_id              BIGINT UNSIGNED NULL COMMENT '集团层节点的实际部门（流转后与模板不同）；模板固定节点为 NULL',
  decision_mode        VARCHAR(16)      NULL COMMENT '发起时冻结的决议模式；NULL=不适用（⑦归档登记节点默认仅登记不审批）',
  pass_threshold       VARCHAR(16)      NULL COMMENT '发起时冻结的会签阈值；判定与取整规则同 flow_node.pass_threshold（NULL=过半）',
  approver_ids_json    JSON         NOT NULL COMMENT '本节点候选人快照',
  status               VARCHAR(24)  NOT NULL DEFAULT 'pending'
                       COMMENT 'pending=未开始 active=进行中 waiting_supplement=等待补件 approved=已通过 rejected=已驳回 skipped=已跳过 returned=已退回 cancelled=已取消（值域与 doc/enums.md §5 一致）；**列宽必须 ≥18**：waiting_supplement 共 18 字符，原 VARCHAR(16) 会在严格模式下报 Data too long，使「等待补件」无法落库（2026-10-03 运行期实测发现）',
  returned_count       INT          NOT NULL DEFAULT 0 COMMENT '被回退次数（上限 2，REQ-FLOW-021）',
  supplement_requested TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '本节点是否已请求过补件（上限 1 次）',
  add_sign_chain_json  JSON             NULL COMMENT '加签链记录',
  started_at           DATETIME         NULL COMMENT '**本轮**开始时间（不是「首次开始时间」）：节点每次被激活都刷新为当前时间 —— 包括被下一节点「回退上一节点」退回后重新进入 active 的那一次。理由：本轮决议只能统计**本轮主任务** —— 轮次边界就是这个 started_at，判定 SQL 见 `FlowTaskMapper.xml#selectRoundPrimaryByNodeInstance`（`flow_task.created_at >= started_at`）。若沿用「首次开始时间」，回退重审会把上一轮的同意票算作本轮结论，节点未经重新审批即通过（2026-10-03 运行期实测发现）。要追溯「首次进入本节点的时间」，请看 `created_at` 与 `sys_thread` 轨迹',
  finished_at          DATETIME         NULL,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_node_instance (instance_id, node_key),
  KEY idx_node_instance_status (instance_id, status),
  CONSTRAINT fk_node_instance_instance FOREIGN KEY (instance_id) REFERENCES flow_instance (id),
  CONSTRAINT chk_node_status CHECK (status IN ('pending','active','waiting_supplement','approved','rejected','skipped','returned','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点实例（运行时快照）';

-- ============================================================
-- 5.3 审批任务（会签 = 同节点实例多条）
-- ============================================================
CREATE TABLE flow_task (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id      BIGINT UNSIGNED NOT NULL,
  node_instance_id BIGINT UNSIGNED NOT NULL,
  assignee_id      BIGINT UNSIGNED NOT NULL COMMENT '当前处理人',
  origin_assignee_id BIGINT UNSIGNED   NULL COMMENT '转办/改派前的原处理人（转办用本列）',
  delegate_from    BIGINT UNSIGNED     NULL COMMENT '加签来源：发起加签/委托的原审批人 user_id（PRD 7.1 / REQ-FLOW-003）',
  add_sign_type    VARCHAR(8)          NULL COMMENT '加签类型：pre=前加签（加签人先审，审完回到本人） post=后加签（本人审完加签人再审） NULL=非加签任务',
  status           VARCHAR(24)  NOT NULL DEFAULT 'pending'
                   COMMENT 'pending=待处理 agreed=已同意 rejected=已拒绝 transferred=已转办 reassigned=已改派 added_sign=已加签 routed=已流转 rolled_back=已回退 supplement_requested=已请求补件 auto_closed=已自动关闭（值域与 doc/enums.md §6 一致）；**列宽必须 ≥20**：supplement_requested 共 20 字符，原 VARCHAR(16) 会在严格模式下报 Data too long，使「已请求补件」无法落库（2026-10-03 运行期实测发现）',
  opinion          VARCHAR(1000)    NULL COMMENT '审批意见（驳回必填 ≥5 字）',
  decided_at       DATETIME         NULL,
  handover_reason  VARCHAR(255)     NULL COMMENT '转办/改派原因',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_task_assignee_status (assignee_id, status, created_at) COMMENT '待办列表主索引',
  KEY idx_task_instance (instance_id),
  KEY idx_task_node_instance (node_instance_id, status),
  CONSTRAINT fk_task_instance      FOREIGN KEY (instance_id)      REFERENCES flow_instance (id),
  CONSTRAINT fk_task_node_instance FOREIGN KEY (node_instance_id) REFERENCES flow_node_instance (id),
  CONSTRAINT fk_task_assignee      FOREIGN KEY (assignee_id)      REFERENCES sys_user (id),
  CONSTRAINT fk_task_delegate_from FOREIGN KEY (delegate_from)   REFERENCES sys_user (id),
  CONSTRAINT chk_task_status CHECK (status IN ('pending','agreed','rejected','transferred','reassigned','added_sign','routed','rolled_back','supplement_requested','auto_closed')),
  CONSTRAINT chk_task_add_sign_type CHECK (add_sign_type IS NULL OR add_sign_type IN ('pre','post'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务';

-- ============================================================
-- 5.4 集团层流转链（流转 / 回退上一节点 / 回到本部门）
-- ============================================================
CREATE TABLE flow_routing (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id     BIGINT UNSIGNED NOT NULL,
  seq             INT          NOT NULL COMMENT '流转序号，从 1 递增',
  action_type     VARCHAR(24)  NOT NULL COMMENT 'route=流转 rollback=回退上一节点 back_home=回到本部门（**不计入** flow_instance.routing_count；连续 back_home ≤2 由本表 seq 的连续记录判定）；旧值 return_node 作废并替换为 rollback（enums.md §14）',
  from_dept_id    BIGINT UNSIGNED NULL,
  to_dept_id      BIGINT UNSIGNED NULL COMMENT 'route 时为承接部门',
  from_node_seq   INT              NULL,
  to_node_seq     INT              NULL,
  designated_by   BIGINT UNSIGNED NOT NULL COMMENT '发起该动作的审批人',
  reason          VARCHAR(255) NOT NULL COMMENT '流转/回退原因（必填）',
  status          VARCHAR(16)  NOT NULL DEFAULT 'processing' COMMENT 'processing=进行中 finished=已完成 cancelled=已取消',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at     DATETIME         NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_routing_seq (instance_id, seq),
  KEY idx_routing_instance (instance_id, status),
  KEY idx_routing_to_dept (to_dept_id, status),
  CONSTRAINT fk_routing_instance FOREIGN KEY (instance_id) REFERENCES flow_instance (id),
  CONSTRAINT chk_routing_action CHECK (action_type IN ('route','rollback','back_home'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集团层流转链（同时是"流转可见性"的判定依据，见 PRD 5.3）；routing_count 只计 route/rollback，**不含 back_home**（REQ-FLOW-022/024）；连续 back_home ≤2 由本表 seq 的连续记录判定';

-- ============================================================
-- 5.5 补件请求（待补件状态 + 次数与时限控制）
-- ============================================================
CREATE TABLE flow_supplement (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id       BIGINT UNSIGNED NOT NULL,
  node_instance_id  BIGINT UNSIGNED NOT NULL COMMENT '请求补件的节点（补完回到这里）',
  requested_by      BIGINT UNSIGNED NOT NULL,
  reason            VARCHAR(500) NOT NULL COMMENT '要求补充什么',
  supplement_round  TINYINT UNSIGNED NOT NULL COMMENT '第几次补件 1..3；**请求补件时占位**（第 N 次请求即第 N 轮），提交补件不改轮次，超时未补沿用同一轮、不消耗新轮次',
  deadline          DATETIME     NOT NULL COMMENT '默认请求后 3 个工作日（工作日口径，超时仅催办）',
  submitted_at      DATETIME         NULL,
  submitted_by      BIGINT UNSIGNED  NULL COMMENT '必须是发起人',
  submitted_note    VARCHAR(500)     NULL COMMENT '补件说明（表单字段 supplement_note，见 doc/forms.md 第 8 节）',
  status            VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending=待补件 submitted=已补件 overdue=已超时 cancelled=已取消（单据在补件期间被终止或撤回时，未完成的补件请求置 cancelled）',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplement_round (instance_id, supplement_round),
  KEY idx_supplement_node (node_instance_id),
  KEY idx_supplement_status_deadline (status, deadline) COMMENT '超时催办扫描索引',
  CONSTRAINT fk_supplement_instance FOREIGN KEY (instance_id)      REFERENCES flow_instance (id),
  CONSTRAINT fk_supplement_node     FOREIGN KEY (node_instance_id) REFERENCES flow_node_instance (id),
  CONSTRAINT chk_supplement_status CHECK (status IN ('pending','submitted','overdue','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补件请求（不算驳回，独立于驳回记录）';
```

---

## 6. 签名、附件、抄送、消息、审计

```sql
-- ============================================================
-- 6.1 签名记录（独立存储、只追加、不可删改；含 CA 预留字段）
-- ============================================================
CREATE TABLE flow_signature (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id        BIGINT UNSIGNED NOT NULL,
  task_id            BIGINT UNSIGNED     NULL,
  user_id            BIGINT UNSIGNED NOT NULL,
  sign_type          VARCHAR(16)  NOT NULL DEFAULT 'handwrite' COMMENT 'handwrite=手写 ca=CA数字签名(二期)',
  sign_image         MEDIUMTEXT       NULL COMMENT '手写签名图（base64 或存储路径）',
  signed_at          DATETIME     NOT NULL COMMENT '签名时间戳（服务端生成）',
  device_fingerprint VARCHAR(128)     NULL,
  ip                 VARCHAR(64)      NULL,
  user_agent         VARCHAR(255)     NULL,
  hash               CHAR(64)     NOT NULL COMMENT '防篡改哈希：覆盖本表全部业务字段（含下方预留字段）',
  -- ===== 二期 CA 预留字段：一期保持 NULL，但结构不可省略（REQ-SIGN-005）=====
  ca_signature       TEXT             NULL COMMENT 'CA 签名值',
  ca_cert_serial     VARCHAR(128)     NULL COMMENT '证书序列号',
  ca_issuer          VARCHAR(255)     NULL COMMENT '证书颁发者',
  tsa_source         VARCHAR(128)     NULL COMMENT '可信时间戳来源',
  verify_result      VARCHAR(32)      NULL COMMENT '验签结果：valid/invalid/expired/revoked',
  verified_at        DATETIME         NULL COMMENT '验签时间',
  -- =====================================================================
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_signature_instance (instance_id),
  KEY idx_signature_user (user_id, signed_at),
  CONSTRAINT fk_signature_instance FOREIGN KEY (instance_id) REFERENCES flow_instance (id),
  CONSTRAINT fk_signature_task     FOREIGN KEY (task_id)     REFERENCES flow_task (id),
  CONSTRAINT fk_signature_user     FOREIGN KEY (user_id)     REFERENCES sys_user (id),
  CONSTRAINT chk_signature_type CHECK (sign_type IN ('handwrite','ca'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签名记录（只追加；见 8.1 不可变约束；PRD 7.1 概览旧名 timestamp → 本表实际列名 signed_at）';

-- ============================================================
-- 6.2 附件（round 区分原始附件与补件）
-- ============================================================
CREATE TABLE flow_attachment (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id   BIGINT UNSIGNED NOT NULL,
  task_id       BIGINT UNSIGNED     NULL COMMENT '补件附件关联的请求；原始附件为空',
  round         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=原始附件 1..3=第 N 次补件',
  field_code    VARCHAR(32)  NOT NULL DEFAULT 'attachments' COMMENT '对应表单字段 ID，见 doc/forms.md',
  file_name     VARCHAR(255) NOT NULL,
  file_size     BIGINT UNSIGNED NOT NULL COMMENT '字节；单文件 ≤50MB 由应用层校验（库级仅存约束）',
  file_ext      VARCHAR(16)  NOT NULL COMMENT '扩展名（白名单校验；**格式白名单由应用层强制**）',
  mime_type     VARCHAR(128)     NULL,
  storage_path  VARCHAR(500) NOT NULL COMMENT '私有化本地存储相对路径，禁止公网直链',
  sha256        CHAR(64)         NULL COMMENT '文件内容哈希，防替换',
  uploader_id   BIGINT UNSIGNED NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_attachment_instance (instance_id, round),
  KEY idx_attachment_uploader (uploader_id),
  CONSTRAINT fk_attachment_instance FOREIGN KEY (instance_id) REFERENCES flow_instance (id),
  CONSTRAINT fk_attachment_task     FOREIGN KEY (task_id)     REFERENCES flow_task (id),
  CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploader_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件（PRD 7.1 概览旧名 file_type → 本表实际列名 file_ext；单文件 ≤50MB 与格式白名单由应用层强制）';

-- ============================================================
-- 6.3 抄送
-- ============================================================
CREATE TABLE flow_cc (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  source      VARCHAR(16)  NOT NULL DEFAULT 'initiator' COMMENT 'initiator=发起人指定 template=模板固定',
  read_at     DATETIME         NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_cc (instance_id, user_id),
  KEY idx_flow_cc_user (user_id, read_at),
  CONSTRAINT fk_flow_cc_instance FOREIGN KEY (instance_id) REFERENCES flow_instance (id),
  CONSTRAINT fk_flow_cc_user     FOREIGN KEY (user_id)     REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄送（只读可见，不产生待办）';

-- ============================================================
-- 6.4 站内信
-- ============================================================
CREATE TABLE sys_message (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL,
  msg_type       VARCHAR(32)  NOT NULL COMMENT 'todo=待办产生 rejected=被驳回 withdrawn=被撤回 collaboration=协同任务（②勾选协同部门后为协同部门负责人产生并行子任务提示） timeout=超时催办（仅催办，不自动跳过/升级） result=结果通知（终审通过/终止） supplement=待补件（补件通知，REQ-FLOW-023） cc=抄送；共 8 值，与 enums.md §8 渠道矩阵一致，PRD 6.7 需同步补入"协同任务""补件通知"',
  title          VARCHAR(100) NOT NULL,
  content        VARCHAR(500)     NULL,
  ref_instance_id BIGINT UNSIGNED NULL,
  read_at        DATETIME         NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_message_user_unread (user_id, read_at, created_at),
  CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信（PRD 7.1 概览旧名 type → 本表实际列名 msg_type）';

-- ============================================================
-- 6.5 审批轨迹（面向展示；与 sys_log 分工：轨迹给人看，日志给审计看）
-- ============================================================
CREATE TABLE sys_thread (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id      BIGINT UNSIGNED NOT NULL,
  node_instance_id BIGINT UNSIGNED NULL,
  seq              INT          NOT NULL COMMENT '轨迹顺序',
  actor_id         BIGINT UNSIGNED NULL,
  actor_name       VARCHAR(50)      NULL COMMENT '快照姓名（防止改名后轨迹失真）',
  actor_position   VARCHAR(50)      NULL COMMENT '快照职务',
  action           VARCHAR(32)  NOT NULL COMMENT 'submit=提交 approve=通过 reject=驳回 route=流转 rollback=回退上一节点 back_home=回到本部门 supplement_request=请求补件 supplement_submit=提交补件 transfer=转办 reassign=改派 add_sign=加签 withdraw=撤回 terminate=终止 skip=跳过（仅事项单②：不涉及费用，财务节点跳过留痕） archive_register=归档登记（⑦，默认仅登记不审批） return_register=归还登记（印鉴单 AC-28 例外：发起人或节点⑦登记归还状态，不推进流程；2026-10-04 由 archive_register 拆出） cc=抄送；共 17 值，与 enums.md §9 一致（旧值 addsign→add_sign、return_node→rollback、archive→archive_register 作废）',
  opinion          VARCHAR(1000)    NULL,
  signature_id     BIGINT UNSIGNED  NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_thread_instance (instance_id, seq),
  CONSTRAINT fk_thread_instance FOREIGN KEY (instance_id) REFERENCES flow_instance (id),
  CONSTRAINT chk_thread_action CHECK (action IN ('submit','approve','reject','route','rollback','back_home','supplement_request','supplement_submit','transfer','reassign','add_sign','withdraw','terminate','skip','archive_register','return_register','cc'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批轨迹';

-- ============================================================
-- 6.6 审计日志（只追加；含权限变更留痕）
-- ============================================================
CREATE TABLE sys_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED     NULL,
  user_name    VARCHAR(50)      NULL COMMENT '快照姓名',
  action       VARCHAR(64)  NOT NULL COMMENT 'login/logout/create/update/delete/approve/route/supplement/grant/publish_template/export…',
  target_type  VARCHAR(32)  NOT NULL COMMENT 'instance/task/user/role/permission/template/org/dict',
  target_id    BIGINT UNSIGNED     NULL,
  before_json  JSON             NULL COMMENT '变更前值（权限变更必填）',
  after_json   JSON             NULL COMMENT '变更后值（权限变更必填）',
  ip           VARCHAR(64)      NULL,
  user_agent   VARCHAR(255)     NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_log_user_time (user_id, created_at),
  KEY idx_log_target (target_type, target_id, created_at),
  KEY idx_log_action_time (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志（只追加；见 8.1）';
```

---

## 7. 快照与数据域实现

### 7.1 审批人快照结构 `flow_instance.approver_snapshot_json`

发起时一次性解析并固化，后续组织变更不影响在途单据（PRD 5.4 / REQ-FLOW-011）。

```json
{
  "template_version": 3,
  "parsed_at": "2026-07-09T10:00:00Z",
  "basis": {
    "initiator_id": 1024,
    "initiator_org_id": 135,
    "initiator_org_path": "/1/12/135/",
    "company_id": 12,
    "category": "economy",
    "involve_cost": true
  },
  "nodes": [
    {
      "node_seq": 1,
      "node_code": "dept_leader",
      "rule": "dept_leader",
      "evidence": "发起人所属科室 135 无负责人配置 → 逐级上溯至部门 120 负责人",
      "approvers": [{ "user_id": 2001, "name": "张三", "org_id": 120 }]
    },
    {
      "node_seq": 2,
      "node_code": "finance_review",
      "rule": "finance_owner",
      "evidence": "集团归口节点：五类事项统一归口财务部（node_code=finance_review），不按类别路由；本单 involve_cost=true 故不跳过",
      "approvers": [{ "user_id": 2002, "name": "李四", "org_id": 210 }]
    },
    {
      "node_seq": 5,
      "node_code": "group_leader",
      "rule": "group_leader",
      "evidence": "按业务线 economy 映射的集团分管领导",
      "approvers": [{ "user_id": 2005, "name": "赵六", "org_id": 30 }]
    },
    {
      "node_seq": 6,
      "node_code": "chairman",
      "rule": "chairman",
      "sign_policy": "required",
      "evidence": "集团董事长，强制签名",
      "approvers": [{ "user_id": 2006, "name": "钱七", "org_id": 30 }]
    },
    {
      "node_seq": 7,
      "node_code": "archive_register",
      "node_type": "archive",
      "rule": "designated",
      "approver_param": { "role_code": "finance_owner" },
      "evidence": "⑦归档登记：财务部内勤，仅登记不审批",
      "approvers": [{ "user_id": 2007, "name": "孙八", "org_id": 210 }]
    }
  ]
}
```

> 上例为节选（主干共 7 节点：①`dept_leader` ②`finance_review` ③`branch_leader` ④`subsidiary_gm` ⑤`group_leader` ⑥`chairman` ⑦`archive_register`），省略的节点结构与上例同构。旧枚举里"按事项类别分流到不同集团归口部门"的那一套节点码（`department` / `finance` / 集团归口部门节点）**一律废弃**：资金/合同/印鉴类单据与「`involve_cost=true`」的事项审批单，节点②恒为 `finance_review`（归口，全单只审一次）；`involve_cost=false` 的事项审批单跳过节点②，节点实例状态置 `skipped` 并在 `sys_thread.action` 记 `skip` 留痕。

**实现要求**

1. 快照中的 `approvers` 为空数组时，**发起必须被拒绝**，提示「XX 节点无有效审批人」（REQ-FLOW-012 / AC-11）。
2. 快照是**运行时权威数据**：`flow_node_instance.approver_ids_json` 从快照派生，审批人的数据可见性以快照为准。
3. 重新提交（驳回后）**重新生成**快照，旧快照在审计日志中保留。

### 7.2 数据域查询口径（对应 PRD 5.3）

| 角色 | 过滤条件（伪 SQL） |
| --- | --- |
| 普通员工 | `i.initiator_id = :uid OR EXISTS(flow_task t WHERE t.instance_id=i.id AND (t.assignee_id=:uid OR t.origin_assignee_id=:uid)) OR EXISTS(flow_cc c WHERE c.instance_id=i.id AND c.user_id=:uid)` |
| 部门负责人 | 上述 OR `i.initiator_org_path LIKE :dept_path_prefix` |
| 子公司总经理 / 分公司管理员 | `i.initiator_company_id = :company_id` |
| 集团财务部（归口角色） | `i.form_type IN ('fund','contract','seal')`（① 归口类别：资金/合同/印鉴证照，恒经节点②） **OR** `i.form_type='matter' AND JSON_EXTRACT(f.fields_json,'$.involve_cost')=TRUE`（② 事项审批单「涉及费用=是」，同样经节点②） **OR** `EXISTS(flow_routing r WHERE r.instance_id=i.id AND r.to_dept_id=:my_dept_id)`（③ 流转链承接给本部门，任意类别） **OR** `i.current_dept_id=:my_dept_id`（④ 当前承接部门为本部门）。**不涉及费用且未经流转的事项审批单不可见**；不再按 `sys_role_category` 限定类别 |
| 集团分管领导 | 按分管业务线映射的类别集合 |
| 集团董事长 / 系统管理员 | 无过滤 |

> **归口统一后的数据域口径（Q10 / Q13，定稿）**：财务部可见性 =「**归口类别（资金/合同/印鉴）恒可见 + 涉及费用的事项单可见 + 流转链可见**；**不涉及费用且未流转的事项单不可见**」，另加"当前承接部门为本部门"。五个事项分类（business/economy/admin/hr/invest，旧码 `operate` 作废并迁移为 `business`）统一归口财务部且**不参与路由**，因此 `sys_role_category` 不再用于"按类别限制财务部的可见范围"——该表仍保留，**仅用于集团分管领导按分管业务线限定范围**。伪 SQL 中 `f` 为 `form_data` 的别名（`f.id = i.form_data_id`），`i` 为 `flow_instance`。

**流转可见性（PRD 5.3，必须实现）**：被流转到的部门对**该张单据**可见，依据 `flow_routing.to_dept_id`；但这条可见性**不得**改写为"该部门可见该类别的全部单据"。实现上必须用上述 `EXISTS(flow_routing …)` 子查询，**不要**把流转记录折算进 `sys_role_category`。

---

## 8. 完整性与不可篡改

### 8.1 审计与签名的不可变约束（对应 AC-20）

应用层**禁用** UPDATE/DELETE 是必要条件，但不充分。数据库层必须再加一道：

```sql
-- MySQL 8.0：用触发器强制拒绝修改与删除
DELIMITER //
CREATE TRIGGER trg_sys_log_no_update BEFORE UPDATE ON sys_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log is append-only';
END//
CREATE TRIGGER trg_sys_log_no_delete BEFORE DELETE ON sys_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log is append-only';
END//
CREATE TRIGGER trg_flow_signature_no_update BEFORE UPDATE ON flow_signature
FOR EACH ROW BEGIN
  -- 仅允许写入验签结果（二期 CA 验签回写），其余字段一律禁止修改
  IF NEW.sign_image  <=> OLD.sign_image
     AND NEW.hash      <=> OLD.hash
     AND NEW.user_id   <=> OLD.user_id
     AND NEW.signed_at <=> OLD.signed_at THEN
    SET NEW.verify_result = NEW.verify_result;  -- 放行
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_signature is append-only';
  END IF;
END//
CREATE TRIGGER trg_flow_signature_no_delete BEFORE DELETE ON flow_signature
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_signature is append-only';
END//
DELIMITER ;
```

> 该触发器是 AC-20 的验收对象：测试人员必须以有写权限的账号直接执行 `UPDATE sys_log …` 并确认被拒绝。

### 8.2 其他关键约束

| 约束 | 实现位置 |
| --- | --- |
| 金额非浮点、金额上限 | `DECIMAL(18,2)` + 服务端禁止浮点运算（`doc/forms.md` 1.5）；**库级只存约束，金额上限（单笔/年度）与格式白名单由应用层强制** |
| 一单一号 | `form_data.biz_no` 与 `flow_instance.biz_no` 双向唯一 |
| 会签同节点多任务 | `flow_task.node_instance_id` 一对多；`flow_node_instance` 上唯一约束为 `(instance_id, node_key)`，其中 `node_key = concat(node_seq,":",node_code,":",IFNULL(dept_id,0))`。**理由**：MySQL 唯一键对 `NULL` 视为互不相同，裸 `dept_id` 无法约束 `dept_id IS NULL` 的模板固定节点，会重复插入（O17） |
| 组织负责人唯一 | `sys_org_leader` 唯一键为 `(org_id, user_id, leader_type, category_key)`，`category_key` 是 `IFNULL(category,'')` 的**生成列**。**理由**：`category` 可空，MySQL 唯一键对 `NULL` 不去重，裸 `category` 会让「同一组织 / 同一人 / 同一 `leader_type`、`category IS NULL`」的行无限重复（2026-10-04 实测：夹具每次执行都堆积重复负责人行）。业务语义不变（NULL 与 NULL 同组、非 NULL 按值区分），与 `sys_user_role.scope_org_key` 同一先例 |
| 回退重审的「本轮」边界 | `flow_node_instance.started_at` = **本轮**开始时间：每次激活（含被「回退上一节点」退回后重新 active）都刷新为当前时间；本轮决议只统计 `flow_task.created_at >= started_at` 的任务（`FlowTaskMapper.xml#selectRoundPrimaryByNodeInstance`）。**理由**：任务只追加不复用，若沿用「首次开始时间」，回退重审会沿用上一轮同意票、节点未经重新审批即通过（2026-10-03 运行期实测发现）。**不得**回退为 `IFNULL(started_at, NOW())` 或「首次开始时间」 |
| 补件轮次唯一 | `flow_supplement` 唯一键 `(instance_id, supplement_round)`；轮次在**请求补件时**占位（第 N 次请求即第 N 轮），提交补件不改轮次，超时未补沿用同一轮 |
| 流转序号唯一 | `flow_routing` 唯一键 `(instance_id, seq)` |
| 流转次数口径 | `flow_instance.routing_count` 只计 `route` 与 `rollback`（≤5），**不含** `back_home`；连续 `back_home` ≤2 由 `flow_routing` 按 `seq` 的连续记录判定（REQ-FLOW-022 / 024）；动作值域 `route` / `rollback` / `back_home`（旧值 `return_node` 作废） |
| 撤回后状态回归 | 撤回动作写入 `sys_thread` 与 `sys_log` 后，`flow_instance.status` **回到 `draft`**（`withdrawn` 为瞬时态、不长期驻留；REQ-FLOW-009） |
| 金额字段导出 | 库级不限制：金额对非财务角色只读不可导出；**仅系统管理员与财务角色可导出**，导出行为写 `sys_log`（REQ-AUTH-003） |
| 敏感字段落点 | `payee_account` 单独密文落列 `form_data.payee_account_cipher`，不进 `fields_json`；列表与详情默认脱敏 |
| 附件体积与格式 | 库级仅存 `file_size` 与 `file_ext`；**单文件 ≤50MB 与扩展名白名单由应用层强制** |
| 会话与设备上限 | `sys_user_session`：在线设备上限为配置项（默认 3），超出踢出 `login_at` 最早的有效会话（REQ-USER-003）；`token_hash` 唯一 |
| 候选人不可为空 | 应用层发起前校验（REQ-FLOW-012） |
| 驳回意见非空且 ≥5 字 | 应用层 + `flow_task.opinion` 长度约束 |
| 模板版本锁定 | `flow_instance.template_version` 发起时写入且不加外键级联更新 |

### 8.3 编号规则

| 编号 | 规则 | 示例 |
| --- | --- | --- |
| 业务单号 `biz_no` | `OA-{四位年}-{六位流水}`，按年重置 | `OA-2026-000123` |
| 组织路径 `path` | 祖先 id 链，含自身，以 `/` 包裹 | `/1/12/135/` |
| 权限码 | `模块:对象:动作` | `flow:task:approve` |

---

## 9. PostgreSQL 差异（如技术方案选 PG）

私有化部署优先 MySQL；若选 PostgreSQL 15+，需注意：

| 项目 | MySQL 8.0 | PostgreSQL 15+ |
| --- | --- | --- |
| JSON 类型 | `JSON` | `JSONB`（推荐，支持 GIN 索引） |
| JSON 索引 | 生成列 + 普通索引 | `CREATE INDEX … USING GIN (fields_json)` |
| 自增主键 | `AUTO_INCREMENT` | `GENERATED ALWAYS AS IDENTITY` |
| 枚举约束 | `CHECK` | `CHECK` 或原生 `ENUM`（推荐 `CHECK`，便于扩展） |
| 不可变表 | 触发器 `SIGNAL` | 规则/触发器 + `REVOKE UPDATE, DELETE ON sys_log FROM app_user`（更简洁，推荐） |
| 大小写 | 不敏感 | 需统一小写标识符或加引号 |
| 时间类型 | `DATETIME` | `TIMESTAMPTZ`（存 UTC，推荐） |

> 无论 MySQL 还是 PG，**表名/字段名/枚举值必须与本文档一致**，避免迁移与交接成本。

---

## 10. 归档策略（对应 REQ-NFR-010）

| 项目 | 规则 |
| --- | --- |
| 归档对象 | `status IN ('approved','rejected','terminated')` 且 `finished_at` 满 **3 年**的实例（`withdrawn` 为瞬时态、撤回后回到 `draft`，不进入归档口径；REQ-FLOW-009） |
| 归档方式 | 整单（`flow_instance` + 节点/任务/轨迹/签名/附件元数据 + `form_data`）搬移至 `_history` 后缀的历史库表 |
| 归档后 | **只读**：仍支持按单号检索、详情预览、审计导出；不可审批、不可撤回、不可补件 |
| 附件文件 | 随元数据一同迁移至冷存储目录，路径在历史库中保持可解析 |
| 签名记录 | **永不归档删除**，随单迁移，保持哈希可校验（10 年保留期内） |
| 清理 | 一期**不做物理删除**；如需清理，须单独评审并留审计记录 |
| 保留期 | 审计日志与审批轨迹 ≥10 年（REQ-NFR-007）；登录日志 1 年 |

---

## 11. 表清单与需求追溯

| # | 表名 | 领域 | 关联需求 |
| --- | --- | --- | --- |
| 1 | sys_org | 组织 | REQ-ORG-001 |
| 2 | sys_user | 组织 | REQ-USER-001~003 |
| 3 | sys_org_leader | 组织 | PRD 5.1 / 5.4 |
| 4 | sys_user_position | 组织 | PRD 5.1（一人多岗） |
| 5 | sys_user_signature | 签名 | REQ-SIGN-002 |
| 6 | sys_login_log | 身份与组织 | REQ-LOG-005 |
| 7 | **sys_user_session** | **会话** | **REQ-USER-003 / REQ-NFR-006（V0.4 新增，DDL 见 2.7）** |
| 8 | sys_role | 权限 | REQ-AUTH-001 |
| 9 | sys_user_role | 权限 | REQ-AUTH-001 |
| 10 | sys_role_category | 权限 | PRD 5.3（仅集团分管领导按业务线限定） |
| 10b | **sys_role_org_node** | 权限 | **PRD 5.2（权限树按组织节点逐级分配；V0.4 补，DDL 见 3.2b）** |
| 11 | sys_permission | 权限 | REQ-ADMIN-003 |
| 12 | sys_role_permission | 权限 | REQ-ADMIN-003 |
| 13 | sys_dict_item | 配置/字典 | REQ-ADMIN-004 / `doc/dict-seed.md` |
| 14 | flow_template | 定义 | REQ-FLOW-006 / REQ-ADMIN-002 |
| 15 | flow_node | 定义 | REQ-FLOW-002 / 003 / 007 / 008 |
| 16 | form_data | 业务 | `doc/forms.md` / REQ-AUTH-003（金额导出与脱敏） |
| 17 | flow_instance | 运行时 | REQ-FLOW-001 / 011 / 024 |
| 18 | flow_node_instance | 运行时 | REQ-FLOW-002 / 021 / 023 |
| 19 | flow_task | 运行时 | REQ-FLOW-003 / 018 / 019 / 023 |
| 20 | flow_routing | 运行时 | REQ-FLOW-020 / 021 / 022 |
| 21 | flow_supplement | 运行时 | REQ-FLOW-023 |
| 22 | flow_signature | 签名 | REQ-SIGN-001 / 004 / 005 |
| 23 | flow_attachment | 附件 | REQ-FLOW-023 / 通用附件管理 |
| 24 | flow_cc | 消息 | REQ-MSG-003 |
| 25 | sys_message | 消息 | REQ-MSG-001 |
| 26 | sys_thread | 审计 | REQ-LOG-002 |
| 27 | sys_log | 审计 | REQ-LOG-001 / 004 / 006 |

> 说明：
>
> 1. **表数量为 27**（V0.4 新增 `sys_user_session`；PRD 7.1 概览的 26 张与本节不一致，以本文档为准）。PRD 7.1 的概览**未单列 6 张表**，逐一点名为：`sys_user_position`、`sys_role_category`、`sys_user_signature`、`sys_dict_item`、`sys_login_log`、`sys_role_permission`；本文档为权威定义。
> 2. **`routing_count` 口径**：只计 `route` 与 `rollback`（上限 5），**不含** `back_home`；「连续 `back_home` ≤2」由 `flow_routing` 按 `seq` 的**连续记录**判定（REQ-FLOW-022 / REQ-FLOW-024）。动作值域定稿为 `route` / `rollback` / `back_home`（旧值 `return_node` 作废，见 `enums.md` §14）。
> 3. **撤回后状态回归**：撤回（REQ-FLOW-009）写入审批轨迹与审计日志后，`flow_instance.status` **回到 `draft`**，`withdrawn` 不长期驻留（PRD 7.2 / 附录 B 同步）。
> 4. **归口部门统计**：`flow_instance.owner_dept_id` 恒为集团财务部，供统计与审计使用（REQ-FLOW-001）；财务部数据域口径见 7.2。
> 5. **列名对照**：`sys_org.org_type`、`sys_user.password_hash`、`flow_signature.signed_at`、`flow_attachment.file_ext`、`sys_message.msg_type` 分别对应 PRD 7.1 概览中的旧名 `type` / `password` / `timestamp` / `file_type` / `type`（各表注释已标注，以本文档为准）。
> 6. **`sys_dict_item` 列清单（V0.4 更新，承载 `doc/dict-seed.md` 的 40 行种子数据）**：`id / dict_type / item_code / item_name / item_name_en / sort_no / status / remark` —— 新增 `item_name_en VARCHAR(64) NULL`（英文名）与 `remark VARCHAR(255) NULL`（备注，如"待业务确认"）；`dict_type` 白名单定稿为 `matter_category` / `contract_type` / `seal_type` / `cert_type` / `payment_method` / `group_dept` / `review_dept_other` / `return_status`（旧 `category` / `pay_method` / `cert_name` 作废，见 `enums.md` §14）。
> 7. **同名辨析**：`sys_dict_item.dict_type = 'group_dept'` 是"集团职能部门"**字典类型**（选项来源，与 `review_dept_other` 同码关联），与已废弃的 `flow_node.node_code = 'group_dept'`（旧集团归口节点，V0.4 已并入 ② `finance_review`）**不是同一个东西**，勿混用。
