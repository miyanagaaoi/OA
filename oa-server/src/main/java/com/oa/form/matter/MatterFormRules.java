package com.oa.form.matter;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.form.app.FormWritePolicy;
import com.oa.form.document.FormRuleContext;
import com.oa.form.document.FormTypeRules;
import com.oa.form.template.validate.ConditionEvaluator;
import com.oa.form.template.validate.FormValidationReport;
import com.oa.workflow.definition.app.SkipConditionEvaluator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * <b>事项审批单（{@code form_type = matter}）专属规则</b> —— 2b.3 第一类。
 *
 * <h2>规则一：「是否涉及费用」是**全系统唯一分支**</h2>
 * <p>真源：
 * <ul>
 *   <li>{@code doc/prd-0.1.md} §6.1 末段原文：「一期的『分支』只剩一处：事项审批单的
 *       「**是否涉及费用**」——决定是否跳过财务部复核节点（见 6.3 节点②）」；</li>
 *   <li>{@code doc/prd-0.1.md} §6.3 主干链原文：「是否涉及费用？涉及→财务部审批；不涉及→跳过本节点」，
 *       以及「**集团归口部门仍记录为「财务部」**：即使事项审批单"不涉及费用"跳过②，
 *       单据的归口部门字段仍记为财务部（用于统计与审计），只是该节点状态为「已跳过」」；</li>
 *   <li>{@code doc/forms.md} §2「业务补充说明」：「{@code involve_cost = 否} 时，流程自动跳过节点②
 *       （财务部复核），轨迹中记录「本单不涉及费用，财务节点已跳过」」；</li>
 *   <li>{@code doc/templates.md} §1.1 ② 行跳过条件：{@code {"field":"involve_cost","op":"eq","value":false}}
 *       —— 与 §1.5 差异摘要「② 是否可跳过：可（{@code involve_cost = false}）」一致。</li>
 * </ul>
 *
 * <p><b>求值实现复用引擎的 {@link SkipConditionEvaluator}</b>：跳过条件的语义只能有一处实现，
 * 否则「表单侧说不跳过、引擎侧说跳过」会造成②既产生待办又被标记 skipped。
 *
 * <h2>规则二：事项类别发起后不可改判</h2>
 * <p>{@code doc/forms.md} §2：「{@code category} **不参与路由** …
 * <b>任何审批节点都不能修改</b>。分类错误的唯一处理路径是驳回给发起人」；
 * {@code doc/prd-0.1.md} §6.1：「发起人选定后**任何节点不可改判**」；
 * 可执行用例 {@code doc/test-cases.md} TC-FORM-003：「尝试用接口把 {@code category} 改为 {@code hr}
 * → 接口返回 403/400，{@code flow_instance.category} 仍为 business」。
 */
@Service
public class MatterFormRules implements FormTypeRules {

    /** 「是否涉及费用」字段码（{@code doc/forms.md} §2 字段表）。 */
    public static final String FIELD_INVOLVE_COST = "involve_cost";

    /** 事项类别字段码。 */
    public static final String FIELD_CATEGORY = "category";

    /** 费用联动字段（{@code doc/templates.md} §2.4 的 {@code linkage.clearWhen} 目标）。 */
    public static final String FIELD_AMOUNT = "amount";

    /** 费用承担主体字段码。 */
    public static final String FIELD_COST_BEARER = "cost_bearer";

    /** ②财务部复核的跳过条件（{@code doc/templates.md} §1.1 / §1.5 原文）。 */
    public static final String FINANCE_SKIP_CONDITION =
            "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}";

    @Override
    public String formType() {
        return "matter";
    }

    // ================================================================ 校验

    @Override
    public void validate(FormRuleContext context, FormValidationReport.Collector collector) {
        validateInvolveCostBranch(context, collector);
        validateCategoryLock(context, collector);
    }

    /**
     * 分支字段与两个联动字段的一致性（SUBMIT 档）。
     *
     * <p>条件必填本身由 schema 的 {@code rules[conditionalRequired]} 承担；
     * 这里补的是「分支字段决定的两个字段必须同生同灭」这一业务口径，
     * 以及归一化之后的反向检查（例如客户端把 {@code involve_cost} 传成字符串 {@code "否"}）。
     */
    private void validateInvolveCostBranch(FormRuleContext context, FormValidationReport.Collector collector) {
        Boolean involveCost = involveCostOf(context.values());
        if (involveCost == null) {
            if (context.mode().isSubmit() && context.schema().knows(FIELD_INVOLVE_COST)) {
                collector.add(FIELD_INVOLVE_COST, labelOf(context, FIELD_INVOLVE_COST, "是否涉及费用"),
                        "required", "请填写是否涉及费用");
            }
            return;
        }
        if (!involveCost) {
            // 「不涉及费用」→ ② 跳过；两个费用字段按 linkage.clearWhen 清空（不是「必填」）
            return;
        }
        if (!context.mode().isSubmit()) {
            return;
        }
        // 「涉及费用」→ ② 不跳过；两个费用字段必填（与 schema 的 conditionalRequired 同口径，重复一次做兜底）
        if (context.schema().knows(FIELD_AMOUNT) && ConditionEvaluator.isEmpty(context.value(FIELD_AMOUNT))) {
            collector.add(FIELD_AMOUNT, labelOf(context, FIELD_AMOUNT, "涉及金额"),
                    "conditionalRequired", "涉及费用时，涉及金额为必填");
        }
        if (context.schema().knows(FIELD_COST_BEARER) && ConditionEvaluator.isEmpty(context.value(FIELD_COST_BEARER))) {
            collector.add(FIELD_COST_BEARER, labelOf(context, FIELD_COST_BEARER, "费用承担主体"),
                    "conditionalRequired", "涉及费用时，费用承担主体为必填");
        }
    }

    /**
     * 类别不可改判：一经发起（即脱离 {@code draft}）后，{@code category} 的**值变化**一律拒绝。
     *
     * <p>{@code draft} 态允许改（驳回后重提也回到 {@code draft}，见 {@code doc/prd-0.1.md} §6.6
     * 「驳回后去向：回到发起人（固定策略）…发起人可修改后重新提交」）。
     *
     * <p>抛 403 {@link ErrorCode#CATEGORY_IMMUTABLE}（不是收集到报告里）——它属于**越权写入**，
     * 与「字段值不合法」是两类问题，错误码必须能区分。
     */
    public void validateCategoryLock(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.touched(FIELD_CATEGORY) || context.draftState()) {
            return;
        }
        Object stored = context.storedValue(FIELD_CATEGORY);
        Object incoming = context.value(FIELD_CATEGORY);
        if (stored == null || incoming == null) {
            return;
        }
        if (!String.valueOf(stored).equals(String.valueOf(incoming))) {
            throw new BizException(ErrorCode.CATEGORY_IMMUTABLE,
                    String.format("事项类别发起后不可改判：已是「%s」，请求改为「%s」。"
                                    + "分类错误的唯一处理路径是驳回给发起人重新提交"
                                    + "（doc/forms.md §2 业务补充说明 / doc/prd-0.1.md §6.1）",
                            stored, incoming))
                    .withDetail("field", FIELD_CATEGORY)
                    .withDetail("stored", stored)
                    .withDetail("incoming", incoming)
                    .withDetail("instanceId", context.instance() == null ? null : context.instance().getId());
        }
    }

    // ================================================================ 归一化

    /**
     * {@code involve_cost = false} 时清空 {@code amount} / {@code cost_bearer}。
     *
     * <p>依据 {@code doc/templates.md} §2.4 的 {@code linkage}：
     * {@code "clearWhen": {"field": "involve_cost", "op": "eq", "value": false}}，
     * 以及示例说明表「{@code involve_cost = false}（默认）| {@code amount} 与 {@code cost_bearer}
     * **隐藏、非必填、值被清空**；流程②被跳过」。
     * 返回 {@code null} 值表示「删除该键」。
     */
    @Override
    public Map<String, Object> normalize(FormRuleContext context) {
        Map<String, Object> patch = new LinkedHashMap<>();
        Boolean involveCost = involveCostOf(context.values());
        if (Boolean.FALSE.equals(involveCost)) {
            if (context.schema().knows(FIELD_AMOUNT)) {
                patch.put(FIELD_AMOUNT, null);
            }
            if (context.schema().knows(FIELD_COST_BEARER)) {
                patch.put(FIELD_COST_BEARER, null);
            }
        }
        return patch;
    }

    // ================================================================ 读取侧

    @Override
    public Map<String, Object> describe(FormRuleContext context) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.putAll(branchOf(context.values()));
        view.put("category", context.value(FIELD_CATEGORY));
        view.put("categoryMutable", context.draftState());
        view.put("categoryLockEvidence",
                "doc/forms.md §2：类别不参与路由；任何审批节点都不能修改；唯一处理路径是驳回给发起人");
        return view;
    }

    /**
     * 分支判定（**纯函数**）：{@code involve_cost} 决定②是否跳过。
     *
     * <p>返回的 {@code ownerDeptRecorded} 恒为「财务部」—— 依据
     * {@code doc/prd-0.1.md} §6.3「即使…跳过②，单据的归口部门字段仍记为财务部（用于统计与审计）」。
     */
    public static Map<String, Object> branchOf(Map<String, Object> values) {
        Boolean involveCost = involveCostOf(values);
        boolean skip = Boolean.FALSE.equals(involveCost);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("field", FIELD_INVOLVE_COST);
        view.put("involveCost", involveCost);
        view.put("skipFinanceReview", skip);
        view.put("skippedNode", skip ? 2 : null);
        view.put("skippedNodeCode", skip ? "finance_review" : null);
        view.put("skipCondition", FINANCE_SKIP_CONDITION);
        view.put("branchIsUnique", true);
        view.put("ownerDeptRecorded", "财务部");
        view.put("evidence", skip
                ? "doc/forms.md §2：involve_cost = 否 时流程自动跳过节点②（财务部复核），"
                        + "轨迹中记录「本单不涉及费用，财务节点已跳过」"
                : "doc/prd-0.1.md §6.3：涉及费用 → 财务部复核不跳过（集团归口节点只审一次）");
        view.put("flowMutation", skip
                ? "节点②状态记为 skipped 且不产生待办；归口部门仍记为财务部"
                : "节点②按 7 节点主干正常产生待办");
        return view;
    }

    /**
     * 按引擎同源的跳过条件求值（{@link SkipConditionEvaluator}）——
     * 与 {@link #branchOf} 结果必须一致，一致性由单测锁定。
     */
    public static boolean shouldSkipFinanceReview(Map<String, Object> values) {
        return SkipConditionEvaluator.matches(FINANCE_SKIP_CONDITION, values);
    }

    /** 「是否涉及费用」的宽松解析（缺省 = {@code false}，与模板 {@code defaultValue: false} 一致）。 */
    public static Boolean involveCostOf(Map<String, Object> values) {
        if (values == null || !values.containsKey(FIELD_INVOLVE_COST)) {
            return Boolean.FALSE;
        }
        Boolean parsed = ConditionEvaluator.booleanOf(values.get(FIELD_INVOLVE_COST));
        return parsed == null ? Boolean.FALSE : parsed;
    }

    private static String labelOf(FormRuleContext context, String code, String fallback) {
        return context.schema().field(code).map(field -> field.label()).orElse(fallback);
    }

    /** 三态白名单里事项单的字段全集判定口径（供出参与测试引用）。 */
    public static FormWritePolicy.FormType formTypeEnum() {
        return FormWritePolicy.FormType.MATTER;
    }
}
