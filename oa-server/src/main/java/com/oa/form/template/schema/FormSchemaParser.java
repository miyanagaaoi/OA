package com.oa.form.template.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.form.dict.DictType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code form_schema_json} → {@link FormSchema} 的**解析与自检**（doc/templates.md §2）。
 *
 * <h2>本类承担的自检（结构契约的机检点）</h2>
 * <ol>
 *   <li>顶层必填键：{@code form_type} / {@code template_code} / {@code schema_version} / {@code fields}；</li>
 *   <li>字段项必填键：{@code code} / {@code label} / {@code type} / {@code rules}（§2.2；
 *       无条件必填时 {@code rules} 可为空数组，**不得为 {@code null}**）；</li>
 *   <li>字段 code 形状：小写蛇形（§2.2「小写蛇形；写入 {@code form_data.fields_json} 的键名；
 *       **一经使用不得复用**」），且**同一 schema 内不得重名**；</li>
 *   <li>字段类型必须属于 {@code enums.md} §11 的 14 种之一；</li>
 *   <li>{@code optionsSource.dictType} 必须属于 {@code doc/dict-seed.md} §0.3 白名单
 *       （§8 原文：「{@code form_schema_json.fields[].optionsSource.dictType} 必须存在于
 *       dict-seed.md 的字典白名单」）；</li>
 *   <li>{@code sections[].fields} 引用的字段码必须已在 {@code fields[]} 中登记（悬空分组会渲染出空行）。</li>
 * </ol>
 *
 * <p>任一不满足即 **400 / {@link ErrorCode#FLOW_DEFINITION_INVALID}**（与模板发布前校验同码，
 * {@code doc/templates.md} §3.3：校验不过 → {@code 400 / 40008}）。**不降级**：解析失败时宁可拒绝，
 * 也不给出一份「缺字段」的 schema —— 那会让服务端二次校验对着错误的字段集放行。
 */
public final class FormSchemaParser {

    /** 字段 code 形状（§2.2「小写蛇形」）。 */
    private static final Pattern FIELD_CODE = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private FormSchemaParser() {
    }

    /**
     * 解析 {@code form_schema_json}。
     *
     * @param raw          模板行里的 JSON 文本
     * @param formTypeHint 调用方已知的单据类型（与 schema 内 {@code form_type} 不一致时报错；
     *                     为 {@code null} 时不做交叉校验）
     * @param versionHint  调用方已知的模板版本（与 {@code schema_version} 不一致时报错；为 {@code null} 时跳过）
     */
    public static FormSchema parse(String raw, String formTypeHint, Integer versionHint) {
        if (raw == null || raw.isBlank()) {
            throw invalid("模板缺少 form_schema_json：无法驱动字段渲染与服务端二次校验", formTypeHint, versionHint);
        }
        JsonNode root;
        try {
            root = JsonText.read(raw);
        } catch (IllegalArgumentException ex) {
            throw invalid("form_schema_json 不是合法 JSON：" + ex.getMessage(), formTypeHint, versionHint);
        }
        if (root == null || !root.isObject()) {
            throw invalid("form_schema_json 顶层必须是 JSON 对象（doc/templates.md §2.1）",
                    formTypeHint, versionHint);
        }

        String formType = text(root, "form_type");
        if (formType == null || formType.isBlank()) {
            throw invalid("form_schema_json 缺少必填键 form_type（doc/templates.md §2.1）",
                    formTypeHint, versionHint);
        }
        formType = formType.trim();
        if (formTypeHint != null && !formTypeHint.isBlank() && !formType.equalsIgnoreCase(formTypeHint.trim())) {
            throw invalid(String.format("form_schema_json.form_type=%s 与模板行的 form_type=%s 不一致",
                    formType, formTypeHint), formTypeHint, versionHint);
        }

        String templateCode = text(root, "template_code");
        if (templateCode == null || templateCode.isBlank()) {
            throw invalid("form_schema_json 缺少必填键 template_code（doc/templates.md §2.1）",
                    formType, versionHint);
        }

        JsonNode versionNode = root.get("schema_version");
        if (versionNode == null || !versionNode.isNumber()) {
            throw invalid("form_schema_json 缺少必填键 schema_version（整数；§2.1）", formType, versionHint);
        }
        int schemaVersion = versionNode.asInt();
        if (versionHint != null && schemaVersion != versionHint) {
            // §2.1：「schema_version 必须等于发布时的 flow_template.version」（V-08 版本号单调递增、不得跳号）
            throw invalid(String.format("form_schema_json.schema_version=%d 与模板版本 %d 不一致"
                            + "（doc/templates.md §2.1 / §3.2 V-08：两者共用同一版本号，同一次发布同时升版）",
                    schemaVersion, versionHint), formType, versionHint);
        }

        List<FormFieldDef> fieldDefs = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray() || fieldsNode.isEmpty()) {
            throw invalid("form_schema_json.fields 必须是非空数组（字段顺序即界面与打印稿顺序；§2.1）",
                    formType, versionHint);
        }
        for (JsonNode item : fieldsNode) {
            if (item == null || !item.isObject()) {
                throw invalid("form_schema_json.fields[] 的每一项都必须是对象（§2.2）", formType, versionHint);
            }
            FormFieldDef def = FormFieldDef.from(item);
            if (def.code() == null || def.code().isBlank()) {
                throw invalid("form_schema_json.fields[] 缺少必填键 code（§2.2）", formType, versionHint);
            }
            String code = def.code().trim();
            if (!FIELD_CODE.matcher(code).matches()) {
                throw invalid(String.format("字段 code「%s」不符合小写蛇形约定（§2.2）", code), formType, versionHint);
            }
            if (!codes.add(code)) {
                throw invalid(String.format("字段 code「%s」在同一 schema 内重复（§2.2：一经使用不得复用）",
                        code), formType, versionHint);
            }
            if (def.type() == null) {
                throw invalid(String.format("字段「%s」的 type=「%s」不是 enums.md §11 的 14 种字段类型之一",
                        code, def.rawType()), formType, versionHint);
            }
            if (def.label() == null || def.label().isBlank()) {
                throw invalid(String.format("字段「%s」缺少必填键 label（§2.2）", code), formType, versionHint);
            }
            if (!item.has("rules") || !item.get("rules").isArray()) {
                throw invalid(String.format("字段「%s」缺少必填键 rules 数组（无条件必填时可为空数组，不得为 null；§2.2）",
                        code), formType, versionHint);
            }
            if (def.dictType() != null && DictType.of(def.dictType()).isEmpty()) {
                throw invalid(String.format("字段「%s」的 optionsSource.dictType=「%s」不在字典类型白名单内（%s）"
                                + "—— doc/templates.md §8 / doc/dict-seed.md §0.3",
                        code, def.dictType(), String.join(" / ", DictType.codes())), formType, versionHint);
            }
            if (def.dictType() != null && !def.isOption()) {
                throw invalid(String.format("字段「%s」的 type=%s 不是选项类字段，不得声明 optionsSource"
                                + "（§2.2：options / optionsSource 仅用于 select / multiselect）",
                        code, def.type().code()), formType, versionHint);
            }
            fieldDefs.add(def);
        }

        List<FormSchema.Section> sections = new ArrayList<>();
        JsonNode sectionsNode = root.get("sections");
        if (sectionsNode != null && sectionsNode.isArray()) {
            for (JsonNode item : sectionsNode) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String id = text(item, "id");
                String title = text(item, "title");
                if (id == null || id.isBlank() || title == null || title.isBlank()) {
                    throw invalid("form_schema_json.sections[] 需要 id 与 title（§2.1）", formType, versionHint);
                }
                String printTitle = text(item, "printTitle");
                List<String> sectionFields = new ArrayList<>();
                JsonNode sectionFieldsNode = item.get("fields");
                if (sectionFieldsNode != null && sectionFieldsNode.isArray()) {
                    for (JsonNode fieldCode : sectionFieldsNode) {
                        String referenced = fieldCode == null || fieldCode.isNull() ? null : fieldCode.asText();
                        if (referenced != null && !codes.contains(referenced)) {
                            throw invalid(String.format("sections[%s].fields 引用了未登记的字段码「%s」（§2.1）",
                                    id, referenced), formType, versionHint);
                        }
                        if (referenced != null) {
                            sectionFields.add(referenced);
                        }
                    }
                }
                sections.add(new FormSchema.Section(id, title, printTitle == null ? title : printTitle,
                        !item.has("collapsible") || item.path("collapsible").asBoolean(true), sectionFields));
            }
        }

        Map<String, FormFieldDef> indexed = new LinkedHashMap<>();
        for (FormFieldDef def : fieldDefs) {
            indexed.put(def.code().trim(), def);
        }
        return FormSchema.of(formType, templateCode.trim(), schemaVersion, text(root, "published_at"),
                sections, indexed, raw);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static BizException invalid(String message, String formType, Integer version) {
        return new BizException(ErrorCode.FLOW_DEFINITION_INVALID, message)
                .withDetail("formType", formType)
                .withDetail("templateVersion", version)
                .withDetail("source", "com.oa.form.template.schema.FormSchemaParser");
    }

    /** 单据类型码形状（{@code matter/fund/contract/seal}；未知即 400）。 */
    public static String requireKnownFormType(String formType) {
        String normalized = formType == null ? null : formType.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || !Set.of("matter", "fund", "contract", "seal").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("未知的单据类型「%s」：合法取值为 matter / fund / contract / seal"
                            + "（doc/enums.md §10.2）", formType == null ? "" : formType))
                    .withDetail("formType", formType);
        }
        return normalized;
    }

    // ================================================================ 版本同步（模板开新版本时用）

    /**
     * 读取 {@code form_schema_json} 里**自称**的 {@code schema_version}（解析不了返回 {@code null}）。
     *
     * <p>用途：模板开新版本时做一致性披露 —— 若自称版本与新版本号不同，
     * 说明该 JSON 是从旧版本整段克隆来的（见 {@link #syncSchemaVersion}）。
     */
    public static Integer declaredSchemaVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JsonText.read(raw);
            JsonNode version = root == null ? null : root.get("schema_version");
            return version != null && version.isNumber() ? version.asInt() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 把 {@code form_schema_json.schema_version} 改写为目标版本（**模板开新版本时必须调用**）。
     *
     * <h2>为什么必须同步（否则整类单据会直接不可用）</h2>
     * <p>{@code doc/templates.md} §2.1：「{@code schema_version} **必须等于发布时的
     * {@code flow_template.version}**（一期表单与流程共用同一版本号，同一次发布同时升版）」；
     * §3.2 V-08：「{@code schema_version} 必须等于对应 {@code flow_template.version}」。
     * 而「开新版本」（{@code POST /flow-templates/{id}/versions}，templates.md §4.4）是**整段克隆**旧版本行，
     * 克隆出来的 JSON 自称的版本仍是旧版本号 —— 若不改写，该新版本一发布就会让
     * {@link FormSchemaParser#parse} 在**每一次建草稿**时抛 40008（版本不一致），
     * 即「管理员点一次开新版本 → 该单据类型当天无法发起任何单据」。
     * 2026-10-03 运行期实测命中（本地库 fund 模板被历史测试升到 v5，其 JSON 仍写 {@code schema_version: 1}）。
     *
     * @param raw    旧版本的 {@code form_schema_json}（{@code null}/空 → 原样返回）
     * @param target 目标版本号（{@code <= 0} 时原样返回）
     * @return 改写后的 JSON 文本；输入不是 JSON 对象时原样返回（不阻断「只配流程不配表单」的模板）
     */
    public static String syncSchemaVersion(String raw, Integer target) {
        if (raw == null || raw.isBlank() || target == null || target <= 0) {
            return raw;
        }
        JsonNode root;
        try {
            root = JsonText.read(raw);
        } catch (IllegalArgumentException ex) {
            return raw;
        }
        if (root == null || !root.isObject()) {
            return raw;
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("schema_version", target);
        return root.toString();
    }
}
