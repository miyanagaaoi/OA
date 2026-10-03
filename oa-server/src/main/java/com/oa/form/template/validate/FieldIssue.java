package com.oa.form.template.validate;

/**
 * 一条校验失败项 —— <b>可定位到「字段码 + 原因」</b>（2b.1 的硬要求）。
 *
 * @param fieldCode 字段 code（未知字段也是它——正是「夹带」的那一个键）
 * @param label     字段标签（未知字段时等于字段码）
 * @param rule      命中的规则类型（{@code doc/templates.md} §2.3 的 {@code type}，
 *                  或本工程补充的 {@code unknownField} / {@code typeMismatch} / {@code dateFormat}
 *                  这类结构性规则名）
 * @param message   给终端用户看的中文文案
 */
public record FieldIssue(String fieldCode, String label, String rule, String message) {

    /** 单行展示（写进 {@code BizException} 的 message，便于前端与排障直接看）。 */
    public String describe() {
        String name = label == null || label.isBlank() || label.equals(fieldCode)
                ? fieldCode : fieldCode + "（" + label + "）";
        return name + "：" + message;
    }
}
