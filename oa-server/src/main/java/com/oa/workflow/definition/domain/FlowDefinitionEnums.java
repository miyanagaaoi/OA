package com.oa.workflow.definition.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 流程定义（模板 / 节点）枚举值域 —— <b>单一代码落点</b>。
 *
 * <p><b>权威源</b>（改这里之前必须先改文档）：
 * <ul>
 *   <li>{@link NodeCode}：doc/enums.md §2「流程节点码（主干 7 个审批节点）」；</li>
 *   <li>{@link ApproverRule}：doc/enums.md §3「审批人解析规则码」（共 <b>9</b> 条）；</li>
 *   <li>{@link DecisionMode}：doc/prd-0.1.md §5.4「节点决议模式」+ doc/enums.md §7；</li>
 *   <li>{@link SignPolicy}：doc/templates.md §1.0「默认强制签名」+ doc/enums.md §7；</li>
 *   <li>{@link TemplateStatus}：doc/templates.md §3.3「模板状态机」；</li>
 *   <li>{@link NodeType}：doc/data-model.md §4.2 {@code flow_node.node_type}。</li>
 * </ul>
 *
 * <p><b>为什么不复用 flow_node 的 DDL 注释字符串</b>：DDL 注释是「给人看的说明」，
 * 而这里是「引擎据此取人与判定的值域」。二者必须逐字一致（本类的
 * {@code FlowDefinitionEnumsTest} 会对 DDL / enums.md 的取值做一致性断言），
 * 但不共享同一份文本，避免解析注释这种脆弱做法。
 *
 * <p><b>旧值一律拒绝</b>：enums.md §14 的迁移对照表（{@code department} / {@code department_leader}
 * / {@code company_exec} / {@code gm} / {@code group_exec} / {@code archive} / {@code group_dept}
 * / {@code finance} / {@code operate} …）在新数据中不得出现，{@link #legacyHint(String)}
 * 只用于给出可读的纠错文案。
 */
public final class FlowDefinitionEnums {

    private FlowDefinitionEnums() {
    }

    // ================================================================ 节点码

    /** 主干 7 个审批节点码（doc/enums.md §2）。顺序即主干顺序。 */
    public enum NodeCode {

        /** ① 直属部门负责人。 */
        DEPT_LEADER(1, "直属部门负责人"),
        /** ② 财务部复核（＝集团归口部门，只审一次；唯一可被跳过的节点）。 */
        FINANCE_REVIEW(2, "财务部复核"),
        /** ③ 分公司分管领导。 */
        BRANCH_LEADER(3, "分公司分管领导"),
        /** ④ 子公司总经理。 */
        SUBSIDIARY_GM(4, "子公司总经理"),
        /** ⑤ 集团分管领导（默认强制签名）。 */
        GROUP_LEADER(5, "集团分管领导"),
        /** ⑥ 集团董事长（默认强制签名）。 */
        CHAIRMAN(6, "集团董事长"),
        /** ⑦ 归档登记（默认「仅登记不审批」）。 */
        ARCHIVE_REGISTER(7, "归档登记");

        private final int seq;
        private final String label;

        NodeCode(int seq, String label) {
            this.seq = seq;
            this.label = label;
        }

        /** 主干序号（与 PRD 6.3 的 ①②③… 对应）。 */
        public int seq() {
            return seq;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<NodeCode> of(String code) {
            return byCode(values(), code);
        }

        /** 主干 7 节点的码列表（顺序固定，templates.md §1.0「主干节点数固定 7 个」）。 */
        public static List<String> trunkCodes() {
            return Arrays.stream(values()).map(NodeCode::code).toList();
        }
    }

    // ================================================================ 节点类型

    /** {@code flow_node.node_type}（doc/data-model.md §4.2）。 */
    public enum NodeType {

        /** 审批节点（①–⑥）。 */
        APPROVE,
        /** 抄送节点（一期无独立行，保留值域）。 */
        CC,
        /** 条件节点（**二期预留**，一期一律拒绝）。 */
        CONDITION,
        /** 归档登记节点（⑦）。 */
        ARCHIVE;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<NodeType> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 决议模式 / 签名策略

    /** 节点决议模式（doc/prd-0.1.md §5.4）。 */
    public enum DecisionMode {

        /** 或签：任一人通过即节点通过（**默认**）。 */
        ANY("或签"),
        /** 会签：同意人数达到阈值才通过。 */
        ALL("会签"),
        /** 依次审批：按配置顺序串行，全部通过才通过。 */
        SEQUENCE("依次审批");

        private final String label;

        DecisionMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<DecisionMode> of(String code) {
            return byCode(values(), code);
        }
    }

    /** 节点签名策略（doc/templates.md §1.0、REQ-SIGN-003）。 */
    public enum SignPolicy {

        /** 强制签名（⑤ 集团分管领导、⑥ 集团董事长 默认）。 */
        REQUIRED("强制"),
        /** 可选签名（① ② ③ ④ 默认）。 */
        OPTIONAL("可选"),
        /** 不签名（⑦ 归档登记 默认）。 */
        NONE("不签名");

        private final String label;

        SignPolicy(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<SignPolicy> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 模板状态

    /** 模板状态机（doc/templates.md §3.3）。 */
    public enum TemplateStatus {

        /** 草稿：唯一可编辑态，不可被新实例使用。 */
        DRAFT(false),
        /** 已发布：只读；同一 {@code code} 下**最多一个**。 */
        PUBLISHED(true),
        /** 已归档：只读，不可被新实例使用（**在途实例继续执行**）。 */
        ARCHIVED(false);

        private final boolean usableByNewInstance;

        TemplateStatus(boolean usableByNewInstance) {
            this.usableByNewInstance = usableByNewInstance;
        }

        /** 是否可被**新**实例使用（AC-09 / templates.md §3.3）。 */
        public boolean usableByNewInstance() {
            return usableByNewInstance;
        }

        /** 是否只读（已发布 / 已归档不可再编辑，改配置必须开新版本）。 */
        public boolean readOnly() {
            return this != DRAFT;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<TemplateStatus> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 审批人解析规则

    /**
     * 审批人解析规则码（doc/enums.md §3，**共 9 条**）。
     *
     * <p>命名空间与 {@link NodeCode} <b>独立</b>：{@code branch_leader} 既是节点码也是规则码，
     * 二者不构成耦合（enums.md §3 的「命名空间独立」说明）。
     */
    public enum ApproverRule {

        /** 直属部门负责人上溯：取发起人科室负责人，科室未设则上溯部门负责人；仍为空则禁止发起。 */
        DEPT_LEADER_UPWARD("直属部门负责人上溯", true, false),
        /** 集团财务部负责人：恒取集团财务部负责人（五类统一归口，不细分）。 */
        FINANCE_OWNER("集团财务部负责人", true, false),
        /** 分公司分管领导：按发起人 {@code company_id} 匹配该公司绑定的分管领导。 */
        BRANCH_LEADER("分公司分管领导", true, false),
        /** 子公司总经理：取发起人所属公司的总经理。 */
        SUBSIDIARY_GM("子公司总经理", true, false),
        /** 集团分管领导：按**事项类别**匹配集团层业务线绑定的分管领导。 */
        GROUP_LEADER("集团分管领导", true, false),
        /** 董事长：取集团董事长（唯一）。 */
        CHAIRMAN("董事长", true, false),
        /** 指定人员或角色：由 IT 在流程设计器中固定指定，参数存 {@code approver_param}。 */
        DESIGNATED("指定人员或角色", true, true),
        /** 发起人自选：发起时由发起人从通讯录中选择候选人（**不需要** {@code approver_param}）。 */
        INITIATOR_PICK("发起人自选", true, false),
        /**
         * 协同部门负责人：**仅用于②的并行子任务组**，每个被勾选部门取该部门负责人。
         *
         * <p>勾选值在**审批时**由②的审批人给出（不是设计期的 {@code approver_param}），
         * 因此同样不需要参数。
         */
        COLLAB_DEPT_LEADER("协同部门负责人", false, false);

        private final String label;
        private final boolean trunkUsable;
        private final boolean requiresParam;

        ApproverRule(String label, boolean trunkUsable, boolean requiresParam) {
            this.label = label;
            this.trunkUsable = trunkUsable;
            this.requiresParam = requiresParam;
        }

        public String label() {
            return label;
        }

        /** 是否可用于主干节点（{@code collab_dept_leader} 只用于②的并行子任务，不占主链编号）。 */
        public boolean trunkUsable() {
            return trunkUsable;
        }

        /** 是否必须给出 {@code approver_param}（designated / initiator_pick 需要）。 */
        public boolean requiresParam() {
            return requiresParam;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<ApproverRule> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 旧值提示

    /**
     * enums.md §14 迁移对照表中的**废弃值** → 定稿值（仅用于生成可读的纠错文案）。
     *
     * <p>刻意只做「节点码 / 解析规则码」两列：这两列是本期新增写入面，
     * 残留旧值会让引擎取错人（例如 {@code department_leader} 已废弃为 {@code dept_leader_upward}）。
     */
    private static final Map<String, String> LEGACY_VALUES = legacyValues();

    private static Map<String, String> legacyValues() {
        Map<String, String> map = new LinkedHashMap<>();
        // 节点码旧值（enums.md §14）：department / finance / group_dept → finance_review 等
        map.put("department", "finance_review");
        map.put("finance", "finance_review");
        map.put("group_dept", "finance_review");
        map.put("company_exec", "branch_leader");
        map.put("gm", "subsidiary_gm");
        map.put("group_exec", "group_leader");
        map.put("archive", "archive_register");
        // 解析规则旧值（enums.md §14）
        // 注意：**不要**把 dept_leader / finance_leader 当作旧值 ——
        // `dept_leader` 是节点码①（enums.md §2，且**没有**被 §14 列为废弃），
        // 误当旧值会让全部 7 节点模板都校验不通过。
        map.put("department_leader", "dept_leader_upward");
        map.put("initiator_self", "initiator_pick");
        map.put("coop_dept_leader", "collab_dept_leader");
        return Map.copyOf(map);
    }

    /** 命中废弃值时返回「旧值 → 定稿值」的提示，否则返回 {@code null}。 */
    public static String legacyHint(String code) {
        if (code == null) {
            return null;
        }
        String replacement = LEGACY_VALUES.get(code.trim().toLowerCase(Locale.ROOT));
        return replacement == null ? null : "「" + code + "」是废弃值（enums.md §14），请改为「" + replacement + "」";
    }

    /** 全部审批人解析规则码（用于「规则清单」接口与发布前校验）。 */
    public static Set<String> approverRuleCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (ApproverRule rule : ApproverRule.values()) {
            codes.add(rule.code());
        }
        return codes;
    }

    // ================================================================ 内部

    private static <E extends Enum<E>> Optional<E> byCode(E[] values, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (E value : values) {
            if (value.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
