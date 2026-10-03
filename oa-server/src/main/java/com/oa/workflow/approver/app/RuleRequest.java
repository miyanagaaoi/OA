package com.oa.workflow.approver.app;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次解析的**输入上下文**（把发起人与表单事实固化成规则可读的形状）。
 *
 * <p>依据 doc/data-model.md §7.1 的 {@code basis} 字段：{@code initiator_id}、
 * {@code initiator_org_id}、{@code initiator_org_path}、{@code company_id}、
 * {@code category}、{@code involve_cost}。本记录是它的**入参侧**对偶，
 * 另加两项只在发起时可得的输入：
 * <ul>
 *   <li>{@link #initiatorPicks()}：{@code initiator_pick} 规则由发起人从通讯录选择的人；</li>
 *   <li>{@link #collabDeptIds()}：{@code collab_dept_leader} 规则由②审批人勾选的协同部门
 *       （发起时为空，运行时由 2a.4/2a.5 传入；本工作包只在预检里支持显式传入以便单测与联调）。</li>
 * </ul>
 *
 * @param initiatorId        发起人 user_id
 * @param initiatorOrgId     发起人组织 id（快照）
 * @param initiatorCompanyId 发起人公司 id（快照）
 * @param initiatorOrgPath   发起人组织路径（快照，形如 {@code /1/12/135/}）
 * @param category           事项类别（配置项，**不参与路由**，仅用于⑤集团分管领导的业务线匹配）
 * @param formValues         本次提交的表单字段值（键 = 字段 code；用于跳过条件求值）
 * @param collabSelfExcludeDeptIds 协同勾选时**必须排除**的部门（②所属部门自身，AC-05「不得勾选本节点部门自身」）
 */
public record RuleRequest(
        Long initiatorId,
        Long initiatorOrgId,
        Long initiatorCompanyId,
        String initiatorOrgPath,
        String category,
        List<Long> initiatorPicks,
        List<Long> collabDeptIds,
        Map<String, Object> formValues,
        java.util.Set<Long> collabSelfExcludeDeptIds
) {

    public RuleRequest {
        initiatorPicks = initiatorPicks == null ? List.of() : List.copyOf(initiatorPicks);
        collabDeptIds = collabDeptIds == null ? List.of() : List.copyOf(collabDeptIds);
        collabSelfExcludeDeptIds = collabSelfExcludeDeptIds == null
                ? java.util.Set.of() : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(collabSelfExcludeDeptIds));
        formValues = formValues == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(formValues));
    }

    /** 常用构造：只有发起人 + 业务事实，无自选/协同。 */
    public static RuleRequest of(Long initiatorId, Long initiatorOrgId, Long initiatorCompanyId,
                                 String initiatorOrgPath, String category, Map<String, Object> formValues) {
        return new RuleRequest(initiatorId, initiatorOrgId, initiatorCompanyId, initiatorOrgPath,
                category, List.of(), List.of(), formValues, java.util.Set.of());
    }

    /** 表单字段值（缺省 {@code null}）。 */
    public Object value(String field) {
        return formValues.get(field);
    }

    /** 「是否涉及费用」（仅事项单有；缺省按 {@code true}，即不跳过②）。 */
    public boolean involveCost() {
        Object value = formValues.get("involve_cost");
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
        return !("false".equals(text) || "0".equals(text) || "no".equals(text));
    }

    /** 覆盖「发起人自选」输入（预检/快照共用同一份上下文，避免两处口径分叉）。 */
    public RuleRequest withPicks(List<Long> picks) {
        return new RuleRequest(initiatorId, initiatorOrgId, initiatorCompanyId, initiatorOrgPath,
                category, picks, collabDeptIds, formValues, collabSelfExcludeDeptIds);
    }

    /** 覆盖「协同部门」输入。 */
    public RuleRequest withCollabDepts(List<Long> deptIds) {
        return new RuleRequest(initiatorId, initiatorOrgId, initiatorCompanyId, initiatorOrgPath,
                category, initiatorPicks, deptIds, formValues, collabSelfExcludeDeptIds);
    }

    /** 覆盖「协同勾选时必须排除的部门」（②所属部门自身，AC-05）。 */
    public RuleRequest withCollabSelfExclude(Set<Long> deptIds) {
        return new RuleRequest(initiatorId, initiatorOrgId, initiatorCompanyId, initiatorOrgPath,
                category, initiatorPicks, collabDeptIds, formValues, deptIds);
    }
}
