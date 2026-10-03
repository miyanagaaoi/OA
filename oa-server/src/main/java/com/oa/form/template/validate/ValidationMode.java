package com.oa.form.template.validate;

/**
 * 校验强度（**两档**；来源：{@code doc/forms.md} §1.1「必填 | 不填能否**提交**」+ §3 金额行
 * 「> 0，见 1.5；**提交前必须通过**」）。
 *
 * <table>
 *   <tr><th>档</th><th>用于</th><th>检查项</th></tr>
 *   <tr>
 *     <td>{@link #DRAFT} 保存</td>
 *     <td>建草稿 / 保存草稿（{@code PUT .../draft}）</td>
 *     <td>未知字段（拒）/ 类型 / 长度上限 / 枚举取值 / 金额定点与范围 / 日期格式 / 附件结构 / 条件必填
 *         —— <b>不</b>强制「无条件必填」：草稿允许不完整（forms.md 的必填口径是「不填能否**提交**」）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #SUBMIT} 提交</td>
 *     <td>提交前校验（{@code POST .../validate}、提交动作前置闸门）</td>
 *     <td>DRAFT 的全部 + <b>无条件必填</b> + 条件必填 + 日期不早于今天 / 不早于发起日 + 跨字段区间 +
 *         附件最小个数 + 业务专属规则（四类单据）</td>
 *   </tr>
 * </table>
 *
 * <h2>为什么草稿档不强制必填</h2>
 * <p>{@code doc/forms.md} §1.1 的「必填」定义原文是「**不填能否提交**」——判据绑在**提交**这个动作上；
 * {@code doc/data-model.md} §4.4 的 {@code plan_category} 校验行也写「非必填；缺省时按默认值补全后落库」，
 * 即草稿落库允许缺省。若草稿档也强制必填，用户将无法「先存一半」，与 §1.2「草稿 | 全部可写 |
 * 发起人自由编辑」的语义相冲突。**任何进入审批的路径都必须过 {@link #SUBMIT} 档**，
 * 且该档由引擎提交前置闸门强制（见 {@code FlowEngineService} 的提交链）。
 *
 * <p>如果产品要求「保存草稿也必须填全」，只需把调用方传的档改为 {@link #SUBMIT} 即可
 * （本类不硬编码档位，由调用方显式传入）。
 */
public enum ValidationMode {

    /** 建草稿 / 保存草稿：结构校验，不强制无条件必填。 */
    DRAFT,
    /** 提交前：结构校验 + 全部必填与业务规则。 */
    SUBMIT;

    public boolean isSubmit() {
        return this == SUBMIT;
    }

    /**
     * 解析请求参数里的档位（{@code mode=draft|submit}）。
     *
     * <p>缺省 = {@link #DRAFT}：**保存默认宽松**，提交动作由引擎的提交前置闸门强制 {@link #SUBMIT} 档；
     * 未知取值 → 400（不静默退化为某一档，档位选错会直接决定「必填要不要拦」）。
     */
    public static ValidationMode of(String mode) {
        if (mode == null || mode.isBlank()) {
            return DRAFT;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "draft" -> DRAFT;
            case "submit" -> SUBMIT;
            default -> throw new com.oa.common.error.BizException(
                    com.oa.common.error.ErrorCode.PARAM_INVALID,
                    String.format("未知的校验档 mode=「%s」：合法取值为 draft / submit", mode));
        };
    }
}
