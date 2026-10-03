package com.oa.authz.visibility;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormWritePolicy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 表单字段写入闸门 —— <b>1.6 字段级限制中「金额只读」的服务端唯一入口</b>。
 *
 * <p>组合两条正交规则（缺一不可）：
 * <ol>
 *   <li><b>状态规则</b>：{@link FormWritePolicy} 的三态白名单（草稿可写 / 审批中只读 /
 *       待补件仅附件与补件说明 / 已完结只读，含印鉴单归还状态例外）；</li>
 *   <li><b>角色规则</b>：{@link AmountFieldPolicy} 的金额只读（非系统管理员/财务角色，
 *       载荷里出现金额字段即 403 {@link ErrorCode#AMOUNT_READ_ONLY}）。</li>
 * </ol>
 *
 * <p><b>为什么需要一个服务类</b>：AC-28 要求「前端篡改请求或内部接口越权调用」也被拒绝，
 * 所以判定必须在服务端集中完成；调用方（表单保存接口、字段策略预检接口）只调用本类，
 * 不允许自行拼装两条规则 —— 那正是「各处手写」漏洞的来源。
 *
 * <p>一期不对外提供单据写入接口（PRD §8.1），因此当前的对外可测面是
 * {@code POST /api/v1/authz/field-policy/assert-write}（同一代码路径）。
 */
@Service
public class FormFieldWriteGuard {

    /**
     * 严格判定：任一字段越权（状态或角色）即整单拒绝。
     *
     * @param fields   提交载荷的字段名集合
     * @param allFields 该表单的字段全集（草稿态直接放行全集）
     */
    public void assertWritable(Set<String> fields, FormWritePolicy.FormState state, FormWritePolicy.FormType formType,
                               boolean isInitiator, boolean isArchiveNode, Set<String> allFields) {
        CurrentUser principal = requirePrincipal();
        FormWritePolicy.assertWritable(fields, state, formType, isInitiator, isArchiveNode, allFields,
                AmountFieldPolicy.canWriteAmounts(principal));
    }

    /** 宽容过滤：剥掉状态不可写与角色不可写的字段，返回实际可落库的载荷（增量保存口径）。 */
    public Map<String, Object> filter(Map<String, Object> payload, FormWritePolicy.FormState state,
                                      FormWritePolicy.FormType formType, boolean isInitiator,
                                      boolean isArchiveNode, Set<String> allFields) {
        CurrentUser principal = requirePrincipal();
        return FormWritePolicy.filterWritable(payload, state, formType, isInitiator, isArchiveNode, allFields,
                AmountFieldPolicy.canWriteAmounts(principal));
    }

    /** 角色规则单独判定（不涉及状态；供只关心金额口径的调用方使用）。 */
    public void assertAmountWritable(Set<String> fields) {
        CurrentUser principal = requirePrincipal();
        AmountFieldPolicy.assertWritable(principal, fields);
    }

    /** 当前登录人的字段级限制快照（{@code GET /api/v1/authz/field-policy/amount}）。 */
    public Map<String, Object> amountPolicy(CurrentUser principal) {
        Map<String, Object> policy = new java.util.LinkedHashMap<>();
        boolean writable = AmountFieldPolicy.canWriteAmounts(principal);
        policy.put("amountFieldNames", java.util.List.of(AmountFieldPolicy.AMOUNT_FIELD, "*_amount", "money"));
        policy.put("writable", writable);
        policy.put("exportable", AmountFieldPolicy.canExportAmounts(principal));
        policy.put("writableRoles", java.util.List.of(VisibilityRoles.ADMIN, VisibilityRoles.FINANCE_OWNER));
        policy.put("readOnly", !writable);
        policy.put("reason", writable
                ? "系统管理员/财务角色：金额可写、可导出（PRD §5.3 例外口径，导出另受 oa.authz.export.amount-enabled 约束）"
                : "非财务类角色：金额只读展示、不可写、不可导出（PRD §5.3 / AC-18）");
        return policy;
    }

    /** 字段名集合工具：把载荷键集转为 {@code Set<String>}（保持顺序，便于错误文案稳定）。 */
    public static Set<String> fieldNames(Map<String, ?> payload) {
        return payload == null ? Set.of() : new LinkedHashSet<>(payload.keySet());
    }

    private CurrentUser requirePrincipal() {
        CurrentUser principal = DataScopeContext.current() == null ? null : DataScopeContext.current().getPrincipal();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
