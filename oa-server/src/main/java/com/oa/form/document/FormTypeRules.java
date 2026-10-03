package com.oa.form.document;

import com.oa.form.template.validate.FormValidationReport;
import java.util.Map;

/**
 * 单据类型专属规则的契约（{@code oa.form.{matter,fund,contract,seal}}）。
 *
 * <p>分工：{@code oa.form.template.validate} 负责**与单据类型无关**的 schema 驱动校验；
 * 本接口的实现负责**由业务文档规定、无法只靠 schema 表达**的那部分规则：
 * <ul>
 *   <li>{@code matter}：{@code involve_cost} 是**全系统唯一分支**（决定②财务复核是否跳过）、
 *       类别发起后不可改判（{@code doc/forms.md} §2 / §9.1 / {@code doc/prd-0.1.md} §6.1）；</li>
 *   <li>{@code fund}：金额 &gt; 0 且**禁浮点**、{@code plan_category}/{@code payment_belong}
 *       **只存不用**（不得被路由/数据域/超时引用）（{@code doc/forms.md} §3 / §6.7 / §6.8）；</li>
 *   <li>{@code contract}：必传合同文本字段（空串/仅空白视为未填）、框架合同金额与期限、
 *       履约区间（{@code doc/forms.md} §4）；</li>
 *   <li>{@code seal}：期限、{@code return_status}/{@code return_date} 的闭环与三态例外
 *       （{@code doc/forms.md} §5）。</li>
 * </ul>
 */
public interface FormTypeRules {

    /** 单据类型码（{@code matter} / {@code fund} / {@code contract} / {@code seal}）。 */
    String formType();

    /** 业务专属校验（把问题追加到收集器；**不抛异常**，由调用方统一抛 40011）。 */
    void validate(FormRuleContext context, FormValidationReport.Collector collector);

    /**
     * 归一化 / 派生：返回需要**覆写**（值非 {@code null}）或**删除**（值为 {@code null}）
     * 的字段（如事项单 {@code involve_cost=false} 时按 {@code linkage.clearWhen} 清空
     * {@code amount}/{@code cost_bearer}）。
     *
     * <p>只允许返回**本单据 schema 内**的字段码；调用方会再按白名单过滤。
     */
    default Map<String, Object> normalize(FormRuleContext context) {
        return Map.of();
    }

    /** 读取侧派生信息（分支判定、联动需求、时限披露等）。 */
    Map<String, Object> describe(FormRuleContext context);
}
