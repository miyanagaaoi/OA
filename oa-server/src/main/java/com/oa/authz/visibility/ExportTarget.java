package com.oa.authz.visibility;

import java.util.List;

/**
 * 导出目标（{@code oa.authz.visibility.export}）—— 每个目标携带**权威列清单**，
 * 所有 CSV 导出的表头都必须取自这里，避免「某个导出悄悄多了一列金额」。
 *
 * <p>列名依据：
 * <ul>
 *   <li>{@link #ORG} / {@link #USER} / {@link #ORG_LEADER} / {@link #USER_POSITION} / {@link #USER_ROLE}
 *       —— doc/import-spec.md §9.1 的五张模板列（顺序逐字一致，保证「导出 → 再导入」往返）；</li>
 *   <li>{@link #INSTANCE_LIST}（单据列表）与 {@link #AUDIT_LOG}（审计日志）—— PRD §5.3
 *       「合同金额、资金金额…不可导出」的落点：金额列恒不在可导出列内（默认），
 *       审计日志的 JSON 列还要额外做**金额键剔除**。</li>
 * </ul>
 */
public enum ExportTarget {

    /** 组织主数据（{@code org.csv}，import-spec §9.1）。 */
    ORG("org_import", true,
            List.of("org_path", "org_name", "org_type", "parent_path", "status", "remark"),
            List.of()),

    /** 人员主数据（{@code user.csv}）。注意：{@code phone} 列保留，但取值受 §9.2 约束。 */
    USER("user_import", true,
            List.of("account", "employee_no", "name", "phone", "email", "company_path", "dept_path", "status", "remark"),
            List.of()),

    /** 组织负责人（{@code org_leader.csv}）。 */
    ORG_LEADER("org_leader_import", true,
            List.of("org_path", "user_account", "leader_type", "sort", "business_line", "remark"),
            List.of()),

    /** 一人多岗任职（{@code user_position.csv}）。 */
    USER_POSITION("user_position_import", true,
            List.of("user_account", "org_path", "post_name", "is_primary", "remark"),
            List.of()),

    /** 角色分配（{@code user_role.csv}）。 */
    USER_ROLE("user_role_import", true,
            List.of("user_account", "role_code", "scope_org_path", "remark"),
            List.of()),

    /**
     * 单据列表（阶段 2b 的列表导出目标）。
     *
     * <p>{@code amount} 是**金额列**：非财务类角色（以及默认策略下的一切角色）
     * 都拿不到该列 —— 这是 AC-18「导出按钮不存在/不可用」在服务端的对应物。
     */
    INSTANCE_LIST("instance_list", false,
            List.of("biz_no", "form_type", "category", "initiator", "initiator_org",
                    "current_node", "status", "submitted_at", "finished_at", "amount"),
            List.of("amount")),

    /**
     * 审计日志（{@code sys_log}）。
     *
     * <p><b>金额处理方式与「金额列」不同</b>：日志没有金额列，金额只可能出现在
     * {@code before_json} / {@code after_json} 的**键**里。因此这两列**不列入
     * {@code amountBearingColumns}**（否则会被整列剔除，等于导出物缺列、且表头与数据行错位）；
     * 它们的金额键剔除由 {@link ExportFieldPolicy#redactAmountKeys(String)} 逐值完成 ——
     * 那是无条件执行的（解析失败整列替换为占位值，fail-closed）。
     */
    AUDIT_LOG("audit_log", false,
            List.of("id", "created_at", "user_name", "action", "target_type", "target_id",
                    "before_json", "after_json", "ip"),
            List.of());

    private final String code;

    private final boolean masterData;

    private final List<String> columns;

    /** 「金额承载列」：默认剔除；仅当角色具备金额导出权且策略开启时才保留。 */
    private final List<String> amountBearingColumns;

    ExportTarget(String code, boolean masterData, List<String> columns, List<String> amountBearingColumns) {
        this.code = code;
        this.masterData = masterData;
        this.columns = List.copyOf(columns);
        this.amountBearingColumns = List.copyOf(amountBearingColumns);
    }

    /** 导出目标码（用于 {@code POST /authz/export-check} 的入参）。 */
    public String code() {
        return code;
    }

    /** 是否为主数据导出（**仅系统管理员**，import-spec §9.2 T-11）。 */
    public boolean masterData() {
        return masterData;
    }

    /** 全部列（含金额承载列）。 */
    public List<String> allColumns() {
        return columns;
    }

    /** 金额承载列。 */
    public List<String> amountBearingColumns() {
        return amountBearingColumns;
    }

    /** 按码解析（不区分大小写；未知返回 {@code null}）。 */
    public static ExportTarget of(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        for (ExportTarget target : values()) {
            if (target.code.equalsIgnoreCase(value) || target.name().equalsIgnoreCase(value)) {
                return target;
            }
        }
        return null;
    }
}
