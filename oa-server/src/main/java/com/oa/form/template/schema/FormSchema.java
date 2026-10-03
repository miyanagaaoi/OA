package com.oa.form.template.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 表单模板 schema（{@code flow_template.form_schema_json} 的解析结果）——
 * 顶层结构契约见 {@code doc/templates.md} §2.1。
 *
 * <h2>顶层键（§2.1 表）</h2>
 * <ul>
 *   <li>{@code form_type}（必填，取值见 {@code enums.md} §10.2）；</li>
 *   <li>{@code template_code}（必填，与 {@code flow_template.code} 一致）；</li>
 *   <li>{@code schema_version}（必填，**必须等于发布时的 {@code flow_template.version}**）；</li>
 *   <li>{@code published_at}（可选）；</li>
 *   <li>{@code sections}（可选，字段分组）；</li>
 *   <li>{@code fields}（必填，**顺序即界面与打印稿的字段顺序**）。</li>
 * </ul>
 *
 * <h2>为什么 schema 必须由「实例锁定的模板版本」解析（AC-09）</h2>
 * <p>{@code doc/templates.md} §3.2 V-02「在途实例锁版本：实例发起时锁定 {@code template_version}，
 * 其剩余节点全部按该版本执行」＋ §3.1「表单快照版本 {@code form_data.schema_version} 提交时写入」。
 * 因此 {@link #field(String)} 的字段全集来自**发起时那一行模板**，
 * 而不是「当前 published」——否则管理员发版会让在途单据的字段集凭空变化。
 */
public final class FormSchema {

    private final String formType;
    private final String templateCode;
    private final int schemaVersion;
    private final String publishedAt;
    private final List<Section> sections;
    private final Map<String, FormFieldDef> fields;
    private final String raw;

    private FormSchema(String formType, String templateCode, int schemaVersion, String publishedAt,
                       List<Section> sections, Map<String, FormFieldDef> fields, String raw) {
        this.formType = formType;
        this.templateCode = templateCode;
        this.schemaVersion = schemaVersion;
        this.publishedAt = publishedAt;
        this.sections = Collections.unmodifiableList(sections);
        this.fields = Collections.unmodifiableMap(fields);
        this.raw = raw;
    }

    static FormSchema of(String formType, String templateCode, int schemaVersion, String publishedAt,
                         List<Section> sections, Map<String, FormFieldDef> fields, String raw) {
        return new FormSchema(formType, templateCode, schemaVersion, publishedAt, sections, fields, raw);
    }

    public String formType() {
        return formType;
    }

    public String templateCode() {
        return templateCode;
    }

    /** {@code schema_version}（= 发布时的 {@code flow_template.version}）。 */
    public int schemaVersion() {
        return schemaVersion;
    }

    public String publishedAt() {
        return publishedAt;
    }

    public List<Section> sections() {
        return sections;
    }

    /** 字段全集（**顺序即界面与打印稿顺序**）。 */
    public List<FormFieldDef> fields() {
        return new ArrayList<>(fields.values());
    }

    /** 全部字段码（保序）。 */
    public Set<String> fieldCodes() {
        return new LinkedHashSet<>(fields.keySet());
    }

    /**
     * <b>附件类字段码</b>（{@code type ∈ {file, files}}，保序）。
     *
     * <p>用途：待补件窗口的写白名单必须**按字段类型**放行「所有附件类字段」，而不是写死
     * {@code attachments} 一个字段码 —— 合同单的 {@code counterparty_docs} 同样是附件类字段
     * （{@code doc/forms.md} §4 字段表 {@code 发起后 = 仅补件}），写死字段码会让它在待补件期
     * 「既不能传也不能删」（40304），与 {@code doc/forms.md} §1.2 的意图相悖。
     *
     * <p>判定用 {@link FormFieldDef#isAttachment()}（即 {@link FormFieldType#isAttachment()}），
     * 因此**模板新增一个附件类字段无需改任何白名单代码**。
     */
    public Set<String> attachmentFieldCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (FormFieldDef field : fields.values()) {
            if (field.isAttachment()) {
                codes.add(field.code());
            }
        }
        return codes;
    }

    /** 按字段码取定义。 */
    public Optional<FormFieldDef> field(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(fields.get(code));
    }

    /** 字段是否登记在 schema 内（**未知字段一律拒绝**的判据）。 */
    public boolean knows(String code) {
        return code != null && fields.containsKey(code);
    }

    /** schema 原文（审计与排障用）。 */
    public String raw() {
        return raw;
    }

    /** 出参视图（前端渲染用；顺序即 {@code fields[]}）。 */
    public Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("formType", formType);
        view.put("templateCode", templateCode);
        view.put("schemaVersion", schemaVersion);
        view.put("publishedAt", publishedAt);
        List<Map<String, Object>> sectionViews = new ArrayList<>();
        for (Section section : sections) {
            Map<String, Object> sectionView = new LinkedHashMap<>();
            sectionView.put("id", section.id());
            sectionView.put("title", section.title());
            sectionView.put("printTitle", section.printTitle());
            sectionView.put("collapsible", section.collapsible());
            sectionView.put("fields", section.fields());
            sectionViews.add(sectionView);
        }
        view.put("sections", sectionViews);
        List<Map<String, Object>> fieldViews = new ArrayList<>();
        for (FormFieldDef field : fields.values()) {
            fieldViews.add(field.view());
        }
        view.put("fields", fieldViews);
        return view;
    }

    /** 单选/多选字段里绑定了字典的字段码（下拉校验与打印名解析用）。 */
    public Map<String, String> dictBindings() {
        Map<String, String> bindings = new LinkedHashMap<>();
        for (FormFieldDef field : fields.values()) {
            if (field.dictType() != null) {
                bindings.put(field.code(), field.dictType());
            }
        }
        return bindings;
    }

    /**
     * 字段分组（{@code sections[]}）。
     *
     * @param id          分组 id
     * @param title       界面分组标题
     * @param printTitle  打印稿分组标题（缺省沿用 {@code title}）
     * @param collapsible 界面是否可折叠（默认 {@code true}）
     * @param fields      该分组包含的字段 {@code code} 列表
     */
    public record Section(String id, String title, String printTitle, boolean collapsible, List<String> fields) {
    }
}
