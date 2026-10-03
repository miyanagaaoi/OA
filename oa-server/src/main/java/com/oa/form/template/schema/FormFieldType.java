package com.oa.form.template.schema;

import java.util.Locale;
import java.util.Optional;

/**
 * 表单字段类型（14 种）—— {@code doc/enums.md} §11「字段类型（14 种）」的代码常量。
 *
 * <p>原文：「范围：{@code form_schema_json.fields[].type}。与 {@code forms.md} 1.1 完全对齐」。
 * 取值：{@code text / textarea / number / amount / select / multiselect / date / daterange /
 * user / org / tag / boolean / file / files}。
 *
 * <p><b>为什么是代码常量而不是字典</b>：{@code doc/dict-seed.md} §10 裁决表把「字段类型」列为
 * **枚举**（载体：代码常量；后台**不可**增删）；硬约束 2「枚举值不得下沉为字典」——
 * 做成字典后管理员可新增「富文本」这类渲染器不认识的值 ⇒ 静默失效。
 */
public enum FormFieldType {

    /** 单行文本（受 {@code maxLength} 限制）。 */
    TEXT(false),
    /** 多行文本。 */
    TEXTAREA(false),
    /** 数字（整数或定点数，非金额）。 */
    NUMBER(false),
    /** 金额：{@code DECIMAL(18,2)} 语义，**禁止浮点**、等宽右对齐。 */
    AMOUNT(false),
    /** 单选下拉（选项来源 {@code options} / {@code optionsSource}）。 */
    SELECT(false),
    /** 多选（打印稿以 ☑/☐ 呈现）。 */
    MULTISELECT(false),
    /** 日期 {@code YYYY-MM-DD}。 */
    DATE(false),
    /** 日期区间 {@code [start, end]}，结束 ≥ 开始。 */
    DATERANGE(false),
    /** 人员选择（取通讯录；受数据域限制）。 */
    USER(false),
    /** 组织选择（四级级联；无权限节点不渲染）。 */
    ORG(false),
    /** 自由文本标签，去重。 */
    TAG(false),
    /** 布尔（单勾选框，默认 {@code false} 除非模板显式指定）。 */
    BOOLEAN(false),
    /** 单个附件。 */
    FILE(true),
    /** 多个附件。 */
    FILES(true);

    private final boolean attachment;

    FormFieldType(boolean attachment) {
        this.attachment = attachment;
    }

    /** 是否附件类字段（受 {@code filePolicy} 与 1.4 通用限制约束）。 */
    public boolean isAttachment() {
        return attachment;
    }

    /** 是否文本类（受长度上限约束）。 */
    public boolean isText() {
        return this == TEXT || this == TEXTAREA || this == TAG;
    }

    /** 是否选项类（受 {@code inDict} / {@code options} 约束）。 */
    public boolean isOption() {
        return this == SELECT || this == MULTISELECT;
    }

    /** 解析（去空格、忽略大小写；未知返回 {@link Optional#empty()}）。 */
    public static Optional<FormFieldType> of(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (FormFieldType type : values()) {
            if (type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** 模板里的原始取值。 */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
