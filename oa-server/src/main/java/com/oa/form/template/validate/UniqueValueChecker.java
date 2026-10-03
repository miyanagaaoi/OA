package com.oa.form.template.validate;

/**
 * {@code rules[type=unique]} 的存在性判定端口（模板契约：{@code {"type":"unique","scope":"…"}}，
 * {@code doc/templates.md} §2.3）。
 *
 * <p>真源用例：资金单 {@code contract_ref}「关联合同单号 | 存在时必须为**已通过**的单据号」
 * （{@code doc/forms.md} §3 字段表；模板里写作 {@code "scope": "flow_instance.biz_no"}）。
 *
 * <p>以**端口**形式注入而不是把 Mapper 塞进校验器：校验器保持纯函数可单测
 * （单测传 {@code (scope, value) -> false} 即可），生产由
 * {@code com.oa.form.infra.FormDataMapper#countApprovedInstanceByBizNo} 实现。
 * 未装配（{@code null}）时 {@code unique} 规则**跳过**并记 WARN ——
 * 不静默放行成「永远通过」的假绿：跳过这件事由调用方在装配处显式决定。
 */
@FunctionalInterface
public interface UniqueValueChecker {

    /**
     * @param scope 规则里的 {@code scope} 文本（如 {@code flow_instance.biz_no}）
     * @param value 待校验值（已 trim）
     * @return {@code true} 表示该值在 scope 内**已存在**（即校验通过）
     */
    boolean exists(String scope, String value);
}
