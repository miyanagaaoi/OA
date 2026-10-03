package com.oa.form.dict;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 数据字典类型白名单（8 类）—— <b>2b.4 字典与下拉的取值闸门</b>。
 *
 * <h2>真源与取证</h2>
 * <ul>
 *   <li>{@code doc/dict-seed.md} §0.3「字典类型白名单」原文：
 *       {@code matter_category · contract_type · seal_type · cert_type · payment_method ·
 *       return_status · group_dept · review_dept_other}；</li>
 *   <li>{@code doc/data-model.md} §3.6 {@code sys_dict_item.dict_type} 列注释：
 *       「字典类型白名单（V0.4 定稿，8 类）：matter_category/contract_type/seal_type/cert_type/
 *       payment_method/group_dept/review_dept_other/return_status；旧值 category→matter_category、
 *       pay_method→payment_method、cert_name→cert_type 一律作废」；</li>
 *   <li>{@code doc/forms.md} §6 的「字段 code ↔ 字典类型 {@code dict_type} 对照（V0.4）」表：
 *       6.1 category→{@code matter_category}、6.2 pay_method→{@code payment_method}、
 *       6.3 contract_type→{@code contract_type}、6.4 seal_type→{@code seal_type}、
 *       6.5 cert_name→{@code cert_type}、6.6 return_status→{@code return_status}、
 *       6.9 other_review_depts→{@code review_dept_other}。</li>
 * </ul>
 *
 * <h2>为什么必须是白名单而不是「查得到就行」</h2>
 * <p>字典是**配置项**（管理后台可增删），但「字典类型」本身不是 —— 若允许对任意
 * {@code dict_type} 取数，一个拼错的类型名会**静默返回空下拉**（前端表现为「这个字段没选项」），
 * 而 {@code rules[type=inDict]} 的服务端校验会连带把**所有**取值判为非法。
 * 因此未知类型一律**拒绝**（400），不降级为空列表。
 *
 * <h2>不属于字典的字段（必须显式排除）</h2>
 * <p>{@code plan_category} / {@code payment_belong} 是**布尔 checkbox**，不是字典项
 * （{@code doc/forms.md} §6.7 / §6.8、{@code doc/dict-seed.md} §6 / §7：
 * 「**不得**在管理后台字典中建立 {@code in_plan} / {@code out_plan} / {@code current_month} … 字典项」）。
 * 因此它们**不在**本白名单内，对它们取字典一律 400 —— 这是「防止把 checkbox 做成下拉」的机检点。
 */
public enum DictType {

    /** 事项类别（旧 {@code dict_type = category} 已作废）。 */
    MATTER_CATEGORY("matter_category", "事项类别"),
    /** 合同类型。 */
    CONTRACT_TYPE("contract_type", "合同类型"),
    /** 用印类型（合同单与印鉴单**共用同一字典 code**；旧别名 {@code sign_seal_type} 作废）。 */
    SEAL_TYPE("seal_type", "用印类型"),
    /** 证照类型（字段 code 仍为 {@code cert_name}，字典类型为 {@code cert_type}）。 */
    CERT_TYPE("cert_type", "证照类型"),
    /** 支付方式（旧 {@code dict_type = pay_method} 已作废）。 */
    PAYMENT_METHOD("payment_method", "支付方式"),
    /** 归还状态。 */
    RETURN_STATUS("return_status", "归还状态"),
    /** 集团职能部门（**标签字典**：仅用于打印与统计；组织关系一律以 {@code sys_org} 为准）。 */
    GROUP_DEPT("group_dept", "集团职能部门"),
    /** 其他会审部门（选项来源于 {@code group_dept}，同源同 code）。 */
    REVIEW_DEPT_OTHER("review_dept_other", "其他会审部门");

    private final String code;
    private final String label;

    DictType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** {@code sys_dict_item.dict_type} 取值。 */
    public String code() {
        return code;
    }

    /** 中文名（错误文案用）。 */
    public String label() {
        return label;
    }

    /** 白名单（顺序即 {@code doc/dict-seed.md} §0.3 的书写顺序）。 */
    public static Set<String> codes() {
        Set<String> result = new LinkedHashSet<>();
        for (DictType type : values()) {
            result.add(type.code);
        }
        return Collections.unmodifiableSet(result);
    }

    /** 按 {@code dict_type} 解析（去空格、忽略大小写；未知返回 {@link Optional#empty()}）。 */
    public static Optional<DictType> of(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return Optional.empty();
        }
        String normalized = dictType.trim().toLowerCase(Locale.ROOT);
        for (DictType type : values()) {
            if (type.code.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return code;
    }
}
