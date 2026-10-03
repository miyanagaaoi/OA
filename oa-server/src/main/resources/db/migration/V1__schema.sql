-- ============================================================================
-- V1 建表（27 张表；不含触发器，触发器见 db/trigger/immutable-triggers.sql）
-- ----------------------------------------------------------------------------
-- 生成时间: 2026-10-03T01:50:35.306Z
-- 生成工具: tools/build-flyway-migrations.js（请勿手工编辑；改 oa-deploy/sql 或文档后重跑）
-- 来源: oa-deploy/sql/01-schema.sql ← doc/data-model.md
-- 执行：Flyway 自动按版本顺序执行 V1 → V2 → V3。
-- 字符集 utf8mb4 / 引擎 InnoDB；按文档顺序建表，外键依赖已满足。
-- ============================================================================

-- ============================================================================
-- 集团OA审批系统 · 01 表结构（27 张表 + 不可篡改触发器）
-- ----------------------------------------------------------------------------
-- 生成时间: 2026-10-03T01:49:53.119Z
-- 生成工具: tools/gen-init-sql.js（请勿手工编辑本文件，改文档后重跑）
-- 真源文档: doc/data-model.md
--
-- 执行顺序：按文档顺序执行（身份与组织 → 权限 → 流程定义 → 运行时 → 签名/附件/消息/审计 → 表单数据）。
-- 包含：建表 28 张、索引 61 个、CHECK 11 个、外键若干、不可篡改触发器 4 个。
-- 不可篡改：sys_log 与 flow_signature 由数据库触发器拒绝 UPDATE 与 DELETE（AC-20）；
--           sys_thread（审批轨迹）一期由应用层只追加约束 + 审计校验保证（见 doc/data-model.md 8.1）。
-- 注意：触发器已拆分到 db/trigger/immutable-triggers.sql，由 ImmutableTriggerInitializer 启动时幂等创建
-- 字符集：utf8mb4；引擎：InnoDB。
-- ============================================================================

SET NAMES utf8mb4;

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
  PRIMARY KEY (id),
  UNIQUE KEY uk_org_leader (org_id, user_id, leader_type, category),
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
    (on_supplement_timeout    IS NULL OR on_supplement_timeout    IN ('notify','auto_pass','auto_return'))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板（按版本累积，不覆盖历史；末 5 列为 Q6/Q7 模板级闸门配置，种子取 V0.4 默认值 5 / 3 / 3 / working / notify）';

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
  category             VARCHAR(32)  NOT NULL COMMENT '事项分类（快照，不可改判）：business/economy/admin/hr/invest；配置项，不参与路由；旧码 operate 作废并迁移为 business（enums.md §14）',
  initiator_id         BIGINT UNSIGNED NOT NULL,
  initiator_org_id     BIGINT UNSIGNED NOT NULL COMMENT '发起人组织快照',
  initiator_company_id BIGINT UNSIGNED NOT NULL COMMENT '发起人公司快照（数据域判定）',
  initiator_org_path   VARCHAR(255) NOT NULL COMMENT '发起时组织路径快照',
  approver_snapshot_json JSON       NOT NULL COMMENT '审批人快照，结构见 7.1（REQ-FLOW-011）',
  status               VARCHAR(16)  NOT NULL DEFAULT 'draft'
                       COMMENT 'draft=草稿 approving=审批中 approved=已通过 rejected=已驳回 withdrawn=已撤回（**瞬时态**：撤回写入轨迹与审计后立即回到 draft，不长期驻留） terminated=已终止',
  sub_status           VARCHAR(16)      NULL COMMENT '子状态：NULL / pending_supplement=待补件（值域与 doc/enums.md §4 一致）',
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
  status               VARCHAR(16)  NOT NULL DEFAULT 'pending'
                       COMMENT 'pending=未开始 active=进行中 waiting_supplement=等待补件 approved=已通过 rejected=已驳回 skipped=已跳过 returned=已退回 cancelled=已取消（值域与 doc/enums.md §5 一致）',
  returned_count       INT          NOT NULL DEFAULT 0 COMMENT '被回退次数（上限 2，REQ-FLOW-021）',
  supplement_requested TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '本节点是否已请求过补件（上限 1 次）',
  add_sign_chain_json  JSON             NULL COMMENT '加签链记录',
  started_at           DATETIME         NULL,
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
  status           VARCHAR(16)  NOT NULL DEFAULT 'pending'
                   COMMENT 'pending=待处理 agreed=已同意 rejected=已拒绝 transferred=已转办 reassigned=已改派 added_sign=已加签 routed=已流转 rolled_back=已回退 supplement_requested=已请求补件 auto_closed=已自动关闭（值域与 doc/enums.md §6 一致）',
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
  action           VARCHAR(32)  NOT NULL COMMENT 'submit=提交 approve=通过 reject=驳回 route=流转 rollback=回退上一节点 back_home=回到本部门 supplement_request=请求补件 supplement_submit=提交补件 transfer=转办 reassign=改派 add_sign=加签 withdraw=撤回 terminate=终止 skip=跳过（仅事项单②：不涉及费用，财务节点跳过留痕） archive_register=归档登记（⑦，默认仅登记不审批） cc=抄送；共 16 值，与 enums.md §9 一致（旧值 addsign→add_sign、return_node→rollback、archive→archive_register 作废）',
  opinion          VARCHAR(1000)    NULL,
  signature_id     BIGINT UNSIGNED  NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_thread_instance (instance_id, seq),
  CONSTRAINT fk_thread_instance FOREIGN KEY (instance_id) REFERENCES flow_instance (id)
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

-- （Flyway 不识别 mysql 客户端的 DELIMITER 语法，故不放在本迁移中。）
