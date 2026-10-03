package com.oa.admin.bulk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 错误码 → 修正建议（import-spec §5.4 的 {@code suggestions} 清单）。
 *
 * <p>建议文案的口径：**可直接复制给填报人**（「先导入其父组织行」这类可执行动作），
 * 而不是重复错误码本身。
 */
public final class ImportCodes {

    private static final Map<String, String> SUGGESTIONS = new LinkedHashMap<>();

    static {
        // §4.1 文件与编码
        SUGGESTIONS.put("E-FILE-001", "模板文件名必须与规格一致（org.csv / user.csv / org_leader.csv / user_position.csv / user_role.csv）");
        SUGGESTIONS.put("E-ENC-001", "请用 Excel「CSV UTF-8（逗号分隔）」另存，确保文件以 UTF-8 BOM（EF BB BF）开头");
        SUGGESTIONS.put("E-ENC-002", "文件必须是 UTF-8 编码（不得为 UTF-16 或含替换字符），请另存为 UTF-8");
        SUGGESTIONS.put("E-HDR-001", "表头列名与顺序必须与模板逐字一致：不得改名、增列、减列、换序");
        SUGGESTIONS.put("E-HDR-002", "缺少表头行：第 1 行必须是模板表头");
        SUGGESTIONS.put("E-ROW-001", "每行列数必须等于表头列数，请检查是否有未转义的英文逗号（值含逗号需用双引号包裹）");
        SUGGESTIONS.put("E-ROW-003", "不得存在空行，请删除文件中的空行");
        SUGGESTIONS.put("E-ROW-004", "数据区不得重复出现表头行，请删除重复的表头");
        SUGGESTIONS.put("W-FMT-001", "单元格首尾有空格（系统按 trim 处理），建议清理以免后续比对困难");
        // §4.2 组织
        SUGGESTIONS.put("E-ORG-001", "同一文件内 org_path 不得重复，请合并重复行");
        SUGGESTIONS.put("E-ORG-002", "先导入其父组织行，或修正 parent_path 拼写；父组织不存在时本行必定失败");
        SUGGESTIONS.put("E-ORG-003", "parent_path 必须等于 org_path 去掉最后一段，请核对层级");
        SUGGESTIONS.put("E-ORG-004", "集团根节点的 parent_path 必须留空");
        SUGGESTIONS.put("E-ORG-005", "非集团节点的 parent_path 必填");
        SUGGESTIONS.put("E-ORG-006", "层级必须为 集团→公司→部门→科室；公司必须挂在集团下，科室必须挂在部门下");
        SUGGESTIONS.put("E-ORG-007", "org_type 只能是 集团 / 公司 / 部门 / 科室");
        SUGGESTIONS.put("E-ORG-008", "org_path 不得为空、不得含空段或首尾斜杠，长度 ≤255");
        SUGGESTIONS.put("E-ORG-009", "status 只能是 启用 / 停用");
        SUGGESTIONS.put("E-ORG-010", "组织停用前必须清空该节点整棵子树的在途单据（PRD 5.5），请先办结或流转");
        SUGGESTIONS.put("E-ORG-012", "org_name 必填且 ≤100 字符");
        SUGGESTIONS.put("E-ORG-013", "remark ≤255 字符");
        SUGGESTIONS.put("E-ORG-015", "整棵组织树必须且只能有 1 个集团根节点");
        SUGGESTIONS.put("E-ORG-020", "该行超出当前导入人的数据域（分公司管理员只能导入本公司子树），已按 fail-closed 拒绝");
        SUGGESTIONS.put("W-ORG-014", "部门/科室未设正职负责人：该部门成员发起单据会被拦截（AC-11），建议补设正职");
        SUGGESTIONS.put("W-ORG-016", "父组织已停用：子节点导入后不可作为发起归属");
        SUGGESTIONS.put("W-ORG-017", "停用组织下仍有在职人员，建议先转岗/调出");
        // §4.3 人员
        SUGGESTIONS.put("E-USER-001", "account 必填，8–64 位、字母开头，仅含字母/数字/下划线/点/连字符");
        SUGGESTIONS.put("E-USER-002", "account 全文件唯一且库内唯一，请改名或合并");
        SUGGESTIONS.put("E-USER-003", "phone 必填且为 11 位大陆手机号（^1[3-9]\\d{9}$）");
        SUGGESTIONS.put("E-USER-004", "email 非空时格式必须合法且 ≤128 字符");
        SUGGESTIONS.put("E-USER-005", "company_path 必填，且必须是 org.csv 中已存在的「公司」节点（集团本部填 集团）");
        SUGGESTIONS.put("E-USER-006", "dept_path 非空时必须能在 org.csv 中解析，且为部门/科室");
        SUGGESTIONS.put("E-USER-007", "status 只能是 在职 / 离职（停用由后台单条操作，T-05）");
        SUGGESTIONS.put("E-USER-008", "company_path 只能指向「公司」节点（集团本部人员填 集团）");
        SUGGESTIONS.put("E-USER-009", "离职前必须清空名下待办：请先转办或由系统管理员改派（AC-12）");
        SUGGESTIONS.put("E-USER-010", "name 必填且 ≤50 字符");
        SUGGESTIONS.put("E-USER-011", "dept_path 必须位于 company_path 子树内");
        SUGGESTIONS.put("E-USER-012", "remark ≤255 字符");
        SUGGESTIONS.put("E-USER-013", "账号已存在但姓名不一致（防止误合并到他人账号），请核对人事花名册");
        SUGGESTIONS.put("E-USER-014", "employee_no 必填、1–32 字符、仅含字母/数字与连字符（水印使用）");
        SUGGESTIONS.put("E-USER-015", "employee_no 全文件唯一且库内唯一，请核对人事花名册");
        SUGGESTIONS.put("E-USER-020", "该行归属组织超出当前导入人的数据域，已按 fail-closed 拒绝");
        // §4.4 负责人
        SUGGESTIONS.put("E-LEAD-001", "org_path 必填且必须能在组织表中解析（先导入组织）");
        SUGGESTIONS.put("E-LEAD-002", "user_account 必填且必须能在人员表中解析（先导入人员）");
        SUGGESTIONS.put("E-LEAD-003", "leader_type 只能是 正职 / 副职");
        SUGGESTIONS.put("E-LEAD-004", "同一组织 + 同一业务线只能有一个正职（business_line 留空视为同一组）");
        SUGGESTIONS.put("E-LEAD-005", "business_line 只能是 经营 / 经济 / 行政 / 人力 / 投资 或留空");
        SUGGESTIONS.put("E-LEAD-006", "负责人不得为离职人员");
        SUGGESTIONS.put("E-LEAD-007", "sort 必须是 0–9999 的整数或留空");
        SUGGESTIONS.put("E-LEAD-008", "business_line 仅允许填在 org_type=集团 的节点上");
        SUGGESTIONS.put("E-LEAD-009", "(org_path, user_account, leader_type, business_line) 四元组重复");
        SUGGESTIONS.put("E-LEAD-010", "remark ≤255 字符");
        SUGGESTIONS.put("E-LEAD-020", "该行组织或人员超出当前导入人的数据域，已按 fail-closed 拒绝");
        // §4.5 岗位
        SUGGESTIONS.put("E-POS-001", "org_path 必填且必须能在组织表中解析");
        SUGGESTIONS.put("E-POS-002", "user_account 必填且必须能在人员表中解析");
        SUGGESTIONS.put("E-POS-003", "is_primary 只能是 是 / 否");
        SUGGESTIONS.put("E-POS-004", "同一 (user_account, org_path) 不得重复（唯一键 uk_user_org）");
        SUGGESTIONS.put("E-POS-005", "每人最多一条 is_primary=是");
        SUGGESTIONS.put("E-POS-006", "post_name 必填且 ≤50 字符");
        SUGGESTIONS.put("E-POS-007", "remark ≤255 字符");
        SUGGESTIONS.put("E-POS-020", "该行人员或组织超出当前导入人的数据域，已按 fail-closed 拒绝");
        SUGGESTIONS.put("W-POS-008", "在职人员的 dept_path 未出现在岗位表，建议补一条主岗记录");
        SUGGESTIONS.put("W-POS-009", "岗位组织不在该员工所属公司子树内（跨公司兼职需确认）");
        // §4.6 角色分配
        SUGGESTIONS.put("E-ROLE-001", "role_code 必须是已初始化角色集内的小写蛇形码（导入不创建角色）");
        SUGGESTIONS.put("E-ROLE-002", "user_account 必填且必须能在人员表中解析");
        SUGGESTIONS.put("E-ROLE-003", "(user_account, role_code, scope_org_path) 重复（空数据域归一为同一组）");
        SUGGESTIONS.put("E-ROLE-004", "scope_org_path 非空时必须能在组织表中解析");
        SUGGESTIONS.put("E-ROLE-005", "remark ≤255 字符");
        SUGGESTIONS.put("E-ROLE-020", "该行人员超出当前导入人的数据域，已按 fail-closed 拒绝");
    }

    private ImportCodes() {
    }

    /** 取建议；未登记的错误码返回通用建议。 */
    public static String suggestion(String code) {
        String value = SUGGESTIONS.get(code);
        return value == null ? "请按 import-spec.md 的规则修正该列后重新导入" : value;
    }
}
