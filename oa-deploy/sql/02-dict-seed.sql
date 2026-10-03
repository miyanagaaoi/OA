-- ============================================================================
-- 集团OA审批系统 · 02 数据字典种子（8 个 dict_type）
-- ----------------------------------------------------------------------------
-- 生成器: tools/gen-init-sql.js sha256=4ff51bba65ea
-- 确定性: 无墙钟时间戳/随机量；同一输入重复生成逐字节一致（可安全重跑生成器）。
-- 请勿手工编辑本文件：改文档后重跑本脚本。
-- 真源文档: doc/dict-seed.md sha256=08dd02498530
--
-- 执行顺序：在 01-schema.sql 之后执行；可重复执行（幂等）。
-- 覆盖：cert_type / contract_type / group_dept / matter_category / payment_method / return_status / review_dept_other / seal_type，共 37 项。
-- 注意：payment_belong 与 planned_category 是 checkbox 布尔字段，**不建字典项**（脚本已校验）。
-- ============================================================================

SET NAMES utf8mb4;

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('matter_category', 'business', '经营', 'Business',    10, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'economy',  '经济', 'Economy',     20, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'admin',    '行政', 'Admin',       30, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'hr',       '人力', 'HR',          40, 'active', '集团归口恒为财务部；不参与路由'),
('matter_category', 'invest',   '投资', 'Investment',  50, 'active', 'Q10 新增；集团归口恒为财务部；不参与路由')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('contract_type', 'purchase',     '购销',   'Purchase and Sale', 10, 'active', 'D-01 已按业务确认：不拆「采购/销售」，保留单一「购销」；二期与 PRD 8.2 合同系统台账类型对齐'),
('contract_type', 'service',      '服务',   'Service',           30, 'active', ''),
('contract_type', 'lease',        '租赁',   'Lease',             40, 'active', ''),
('contract_type', 'construction', '工程',   'Construction',      50, 'active', ''),
('contract_type', 'labor',        '劳务',   'Labor Service',     60, 'active', 'D-02 已按业务确认：保留独立劳务类型'),
('contract_type', 'other',        '其他',   'Other',            999, 'active', '选择本项时 contract_type_other 必填')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('seal_type', 'company_seal', '公章',     'Company Seal',              10, 'active', '需填用印份数'),
('seal_type', 'contract_seal','合同章',   'Contract Seal',             20, 'active', '合同单拟用印类型默认值'),
('seal_type', 'finance_seal', '财务章',   'Finance Seal',              30, 'active', '需填用印份数'),
('seal_type', 'legal_seal',   '法人章',   'Legal Representative Seal', 40, 'active', '需填用印份数'),
('seal_type', 'cert_seal',    '证照章',   'Certificate Seal',          50, 'active', 'D-03 已按业务确认：与 cert_borrow 并存'),
('seal_type', 'cert_borrow',  '证照借用', 'Certificate Borrow',        60, 'active', '选择本项时必填 cert_type，且隐藏 seal_count')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('cert_type', 'business_license',     '营业执照',     'Business License',                10, 'active', ''),
('cert_type', 'tax_cert',             '税务登记证',   'Tax Registration Certificate',    20, 'active', ''),
('cert_type', 'org_code',             '组织机构代码证','Organization Code Certificate',  30, 'active', ''),
('cert_type', 'qualification',        '资质证书',     'Qualification Certificate',       40, 'active', ''),
('cert_type', 'other',                '其他',         'Other',                          999, 'active', '')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('payment_method', 'transfer',   '银行转账', 'Bank Transfer',      10, 'active', 'D-05 已按业务确认：按现有纸质实单用词保留「银行转账」，不改名「电汇」'),
('payment_method', 'acceptance', '银行承兑汇票','Bank Acceptance',  20, 'active', ''),
('payment_method', 'cash',       '现金',     'Cash',               30, 'active', ''),
('payment_method', 'other',      '其他',     'Other',             999, 'active', '')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('group_dept', 'econ_dev',     '经发部',       'Economic Development Dept', 10, 'active', '标签字典；审批归口已统一为财务部，本部门仅作协同/会审'),
('group_dept', 'finance',      '财务部',       'Finance Dept',              20, 'active', '标签字典；集团归口部门（恒为财务部）'),
('group_dept', 'hr_dept',      '人力资源部',   'HR Dept',                   30, 'active', '标签字典；仅作协同/会审'),
('group_dept', 'group_office', '集团办',       'Group Office',              40, 'active', '标签字典；仅作协同/会审')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('review_dept_other', 'econ_dev',     '经发部',     'Economic Development Dept', 10, 'active', 'D-09 已按业务确认：与 group_dept.econ_dev 同源同 code'),
('review_dept_other', 'finance',      '财务部',     'Finance Dept',              20, 'active', 'D-09 已按业务确认：与 group_dept.finance 同源同 code'),
('review_dept_other', 'hr_dept',      '人力资源部', 'HR Dept',                   30, 'active', 'D-09 已按业务确认：与 group_dept.hr_dept 同源同 code'),
('review_dept_other', 'group_office', '集团办',     'Group Office',              40, 'active', 'D-09 已按业务确认：与 group_dept.group_office 同源同 code')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, item_code, item_name, item_name_en, sort_no, status, remark) VALUES
('return_status', 'pending',      '未归还',   'Not Returned', 10, 'active', '默认值'),
('return_status', 'returned',     '已归还',   'Returned',     20, 'active', '选择本项时 return_date 必填'),
('return_status', 'not_required', '无需归还', 'Not Required', 30, 'active', 'D-10 已按业务确认：保留三值；本项为终态备注，不参与超期提醒与催办')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_name_en = VALUES(item_name_en),
  sort_no = VALUES(sort_no), status = VALUES(status), remark = VALUES(remark);
