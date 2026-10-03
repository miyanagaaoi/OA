package com.oa.form.app;

import com.oa.authz.visibility.AmountFieldPolicy;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 三态读写白名单（doc/tech-design.md §5.4，服务端强制，纯逻辑可单测）。
 *
 * <table>
 *   <tr><th>单据状态</th><th>可写字段</th></tr>
 *   <tr><td>草稿 {@code draft}</td><td>全部字段</td></tr>
 *   <tr><td>审批中 {@code approving}</td><td>无（全只读）</td></tr>
 *   <tr><td>待补件 {@code pending_supplement}</td><td>仅 {@code attachments} + {@code supplement_note}</td></tr>
 *   <tr><td>已完结（通过/驳回/终止）</td><td>无（只读，含归档）</td></tr>
 * </table>
 *
 * <b>唯一例外</b>：印鉴证照单（{@code form_type = seal}）的 {@code return_status} / {@code return_date}
 * 在「审批中」与「待补件」态下，**仅发起人与节点⑦（归档登记）可改**；其余角色/节点仍全只读。
 *
 * <p>本类只做判定，不做持久化；调用方（{@code form} 领域的应用服务）必须在入库前调用
 * {@link #assertWritable} 或 {@link #filterWritable}，把越权字段直接剥掉 —— 前端只读仅是体验，不是边界。
 */
public final class FormWritePolicy {

    /** 附件字段（forms.md 字段键）。 */
    public static final String FIELD_ATTACHMENTS = "attachments";

    /** 补件说明字段（forms.md §8）。 */
    public static final String FIELD_SUPPLEMENT_NOTE = "supplement_note";

    /** 印鉴单「归还状态」字段。 */
    public static final String FIELD_RETURN_STATUS = "return_status";

    /** 印鉴单「归还日期」字段。 */
    public static final String FIELD_RETURN_DATE = "return_date";

    /** 待补件态下唯一可写的两个字段。 */
    public static final Set<String> SUPPLEMENT_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(java.util.List.of(FIELD_ATTACHMENTS, FIELD_SUPPLEMENT_NOTE)));

    /** 印鉴单唯一例外字段。 */
    public static final Set<String> SEAL_RETURN_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(java.util.List.of(FIELD_RETURN_STATUS, FIELD_RETURN_DATE)));

    private FormWritePolicy() {
    }

    /** 单据状态（三态 + 完结）。 */
    public enum FormState {
        /** 草稿。 */
        DRAFT,
        /** 审批中。 */
        APPROVING,
        /** 待补件。 */
        PENDING_SUPPLEMENT,
        /** 已完结（approved / rejected / terminated / withdrawn 写轨迹后回 draft 除外）。 */
        CLOSED
    }

    /** 单据类型（{@code form_data.form_type}）。 */
    public enum FormType {
        MATTER, FUND, CONTRACT, SEAL;

        public static FormType of(String formType) {
            if (formType == null) {
                return MATTER;
            }
            try {
                return valueOf(formType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return MATTER;
            }
        }
    }

    /**
     * 由流程实例状态推导表单状态。
     *
     * @param instanceStatus {@code flow_instance.status}
     * @param subStatus      {@code flow_instance.sub_status}（{@code pending_supplement}）
     */
    public static FormState resolveState(String instanceStatus, String subStatus) {
        if ("pending_supplement".equalsIgnoreCase(subStatus)) {
            return FormState.PENDING_SUPPLEMENT;
        }
        if (instanceStatus == null) {
            return FormState.DRAFT;
        }
        return switch (instanceStatus.trim().toLowerCase(Locale.ROOT)) {
            case "draft" -> FormState.DRAFT;
            case "approving" -> FormState.APPROVING;
            default -> FormState.CLOSED;
        };
    }

    /**
     * 计算可写字段集合。
     *
     * @param state        表单状态
     * @param formType     单据类型
     * @param isInitiator  当前登录人是否为本单发起人
     * @param isArchiveNode 当前登录人是否处于节点⑦（{@code archive_register}）
     * @param allFields    该表单的字段全集（草稿态直接返回它）
     */
    public static Set<String> writableFields(FormState state, FormType formType, boolean isInitiator,
                                             boolean isArchiveNode, Set<String> allFields) {
        Set<String> result = new LinkedHashSet<>();
        FormState effectiveState = state == null ? FormState.DRAFT : state;
        if (effectiveState == FormState.DRAFT) {
            if (allFields != null) {
                result.addAll(allFields);
            }
            return result;
        }
        if (effectiveState == FormState.CLOSED) {
            // 已完结 / 已归档：一律只读（data-model.md §10「归档后只读」，无任何例外）
            return result;
        }
        if (effectiveState == FormState.PENDING_SUPPLEMENT) {
            result.addAll(SUPPLEMENT_FIELDS);
        }
        // 唯一例外：印鉴单归还状态/日期（审批中 / 待补件态下，仅发起人与节点⑦可改）
        if (formType == FormType.SEAL && (isInitiator || isArchiveNode)) {
            result.addAll(SEAL_RETURN_FIELDS);
        }
        return result;
    }

    /** 单字段可写判定。 */
    public static boolean isWritable(String field, FormState state, FormType formType, boolean isInitiator,
                                     boolean isArchiveNode, Set<String> allFields) {
        if (field == null) {
            return false;
        }
        return writableFields(state, formType, isInitiator, isArchiveNode, allFields).contains(field);
    }

    /** 断言可写，否则抛 403 {@link ErrorCode#FIELD_WRITE_DENIED}。 */
    public static void assertWritable(String field, FormState state, FormType formType, boolean isInitiator,
                                      boolean isArchiveNode, Set<String> allFields) {
        if (!isWritable(field, state, formType, isInitiator, isArchiveNode, allFields)) {
            throw new BizException(ErrorCode.FIELD_WRITE_DENIED,
                    String.format(ErrorCode.FIELD_WRITE_DENIED.getMessage(), field));
        }
    }

    /** 批量断言（任一字段越权即整单拒绝，避免部分写入）。 */
    public static void assertWritable(Set<String> fields, FormState state, FormType formType, boolean isInitiator,
                                      boolean isArchiveNode, Set<String> allFields) {
        if (fields == null) {
            return;
        }
        Set<String> writable = writableFields(state, formType, isInitiator, isArchiveNode, allFields);
        for (String field : fields) {
            if (!writable.contains(field)) {
                throw new BizException(ErrorCode.FIELD_WRITE_DENIED,
                        String.format(ErrorCode.FIELD_WRITE_DENIED.getMessage(), field));
            }
        }
    }

    /** 过滤提交载荷：只保留可写字段（服务端强制，静默丢弃越权字段并保留其余）。
     *
     * <p>与 {@link #assertWritable(Set, FormState, FormType, boolean, boolean, Set)} 的区别：
     * 前者「宽松过滤」（适合增量保存），后者「严格拒绝」（适合提交审批）。
     */
    public static Map<String, Object> filterWritable(Map<String, Object> payload, FormState state, FormType formType,
                                                     boolean isInitiator, boolean isArchiveNode, Set<String> allFields) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (payload == null) {
            return filtered;
        }
        Set<String> writable = writableFields(state, formType, isInitiator, isArchiveNode, allFields);
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (writable.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    // ================================================================ 金额字段级限制（阶段 1.6）

    /**
     * 金额字段判定 —— 委托给 {@code com.oa.authz.visibility.AmountFieldPolicy}（**唯一实现**）。
     *
     * <p>把金额规则接进本类的原因：写入判定必须**一次给全**，否则调用方很容易只调用
     * 三态白名单而漏掉角色规则（PRD §5.3：草稿态下非财务角色同样不能写金额）。
     */
    public static boolean isAmountField(String field) {
        return AmountFieldPolicy.isAmountField(field);
    }

    /**
     * 金额只读断言（角色规则，与单据状态无关）。
     *
     * @param amountWritable 调用人是否具备金额写权限（系统管理员 / 财务角色，见
     *                       {@link AmountFieldPolicy#canWriteAmounts})
     * @throws BizException 403 {@link ErrorCode#AMOUNT_READ_ONLY}
     */
    public static void assertAmountWritable(Set<String> fields, boolean amountWritable) {
        if (amountWritable || fields == null || fields.isEmpty()) {
            return;
        }
        AmountFieldPolicy.assertWritable(null, fields);
    }

    /**
     * 组合断言：**状态白名单 ∧ 角色金额规则**（两者都通过才放行）。
     *
     * <p>这是表单保存路径应当调用的方法；只调 5 参版本等于漏掉 PRD §5.3 的金额只读口径。
     */
    public static void assertWritable(Set<String> fields, FormState state, FormType formType, boolean isInitiator,
                                      boolean isArchiveNode, Set<String> allFields, boolean amountWritable) {
        assertAmountWritable(fields, amountWritable);
        assertWritable(fields, state, formType, isInitiator, isArchiveNode, allFields);
    }

    /**
     * 组合过滤：先按状态白名单过滤，再按角色规则剥掉金额字段（宽容口径，适合增量保存）。
     */
    public static Map<String, Object> filterWritable(Map<String, Object> payload, FormState state, FormType formType,
                                                     boolean isInitiator, boolean isArchiveNode, Set<String> allFields,
                                                     boolean amountWritable) {
        Map<String, Object> filtered = filterWritable(payload, state, formType, isInitiator, isArchiveNode, allFields);
        if (!amountWritable) {
            filtered.keySet().removeIf(FormWritePolicy::isAmountField);
        }
        return filtered;
    }
}
