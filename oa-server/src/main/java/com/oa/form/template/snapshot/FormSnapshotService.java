package com.oa.form.template.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.form.dict.FormDictService;
import com.oa.form.infra.FormDataMapper;
import com.oa.form.infra.row.FormDataFullRow;
import com.oa.form.template.schema.FormFieldDef;
import com.oa.form.template.schema.FormSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <b>2b.1 提交快照与模板版本</b> —— {@code form_data} 上「三层版本」中层的落点。
 *
 * <h2>三层版本（{@code doc/templates.md} §3.1）</h2>
 * <table>
 *   <tr><th>层</th><th>字段</th><th>写入时机</th><th>语义</th></tr>
 *   <tr><td>模板版本</td><td>{@code flow_template.version}</td><td>每次发布 +1</td>
 *       <td>表单与流程**共用同一版本号**</td></tr>
 *   <tr><td><b>表单快照版本</b></td><td>{@code form_data.schema_version}</td><td><b>提交时</b></td>
 *       <td>固化提交时的表单模板版本，防止模板变更影响在途单据</td></tr>
 *   <tr><td>实例锁定版本</td><td>{@code flow_instance.template_version}</td><td>发起时</td>
 *       <td>在途实例的执行依据</td></tr>
 * </table>
 *
 * <p>对应快照规则 V-03：「**提交时固化字段定义与值**：把 {@code form_schema_json} 的
 * {@code schema_version} 与 {@code fields_json} **一并**写入 {@code form_data}」；
 * V-08：「{@code schema_version} 必须等于对应 {@code flow_template.version}」。
 *
 * <h2>读取侧的「历史单据保留当时取值」</h2>
 * <p>{@link #snapshotView} 只把 **code** 原样输出，并用 {@link FormDictService} 解析**当前**中文名
 * （{@code doc/templates.md} §4.2：「修改字典选项（增删选项）→ 在途实例影响：无 ——
 * 字段值为快照存储，历史单据显示原 code 的中文名」）。字典里已删除的 code 仍原样返回，
 * <b>绝不</b>因为「字典里没有了」而丢字段或报错。
 */
@Service
public class FormSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(FormSnapshotService.class);

    private final FormDataMapper formDataMapper;
    private final FormDictService dictService;

    public FormSnapshotService(FormDataMapper formDataMapper, FormDictService dictService) {
        this.formDataMapper = formDataMapper;
        this.dictService = dictService;
    }

    /**
     * 固化字段值与快照版本（**唯一写入口**）。
     *
     * @param formDataId    {@code form_data.id}
     * @param schema        实例锁定版本的 schema
     * @param fields        待落库字段（已过白名单与归一化）
     * @param currentVersion 期望的 {@code schema_version}（一般是 {@code schema.schemaVersion()}）
     */
    public void write(Long formDataId, FormSchema schema, Map<String, Object> fields, Integer currentVersion) {
        if (formDataId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "formDataId 不能为空");
        }
        int version = currentVersion == null ? schema.schemaVersion() : currentVersion;
        if (version != schema.schemaVersion()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    String.format("表单快照版本不一致：期望 v%d（模板 %s），实际 v%d"
                                    + "（doc/templates.md §3.2 V-08：schema_version 必须等于对应 flow_template.version）",
                            schema.schemaVersion(), schema.templateCode(), version));
        }
        String json = fields == null || fields.isEmpty() ? "{}" : JsonText.write(fields);
        formDataMapper.updateFieldsJson(formDataId, json, version);
    }

    /**
     * 快照版本与实例锁定版本的一致性检查（读路径的**披露式**检查）。
     *
     * <p>历史数据可能因迁移口径不一致而漂移；此处只记 WARN 并以**实例锁定版本**为准继续读
     * （AC-09：在途实例按其发起时版本执行），不阻断读取 —— 阻断会让历史单据彻底打不开。
     */
    public void discloseVersionDrift(Long formDataId, Integer storedSchemaVersion, Integer lockedTemplateVersion) {
        if (storedSchemaVersion == null || lockedTemplateVersion == null
                || storedSchemaVersion.equals(lockedTemplateVersion)) {
            return;
        }
        log.warn("表单快照版本漂移：form_data.id={} 的 schema_version={} 与实例锁定版本 v{} 不一致；"
                        + "读取以实例锁定版本为准（doc/templates.md V-02 / V-03）",
                formDataId, storedSchemaVersion, lockedTemplateVersion);
    }

    /** 解析 {@code fields_json}（空/非法 → 空表；**不抛异常**，历史脏数据不应让详情页打不开）。 */
    public Map<String, Object> readFields(String fieldsJson) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (fieldsJson == null || fieldsJson.isBlank()) {
            return values;
        }
        try {
            JsonNode root = JsonText.read(fieldsJson);
            if (root == null || !root.isObject()) {
                return values;
            }
            root.fields().forEachRemaining(entry -> values.put(entry.getKey(), jsonValue(entry.getValue())));
        } catch (RuntimeException ex) {
            log.warn("form_data.fields_json 无法解析，读取按空表处理：{}", ex.getMessage());
        }
        return values;
    }

    /** 读取整行并解析字段（数据域过滤：域外返回 {@code null}）。 */
    public FormDataFullRow readRow(Long formDataId) {
        return formDataId == null ? null : formDataMapper.selectFormDataById(formDataId);
    }

    /**
     * 快照视图（出参）：模板版本 + 字段值 + 选项类字段的**中文名解析**。
     *
     * @param schema 实例锁定版本的 schema
     * @param values 字段值（已解析）
     */
    public Map<String, Object> snapshotView(FormSchema schema, Map<String, Object> values) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("formType", schema.formType());
        view.put("templateCode", schema.templateCode());
        view.put("schemaVersion", schema.schemaVersion());
        view.put("fields", values == null ? Map.of() : values);
        view.put("displayNames", displayNames(schema, values));
        view.put("snapshotEvidence",
                "doc/templates.md §3.1/§3.2：schema_version 与 fields_json 提交时一并固化（V-03）；"
                        + "在途实例按发起时锁定的 template_version 执行（V-02 / AC-09）");
        return view;
    }

    /** 选项类字段的中文名（`字段码 → 名称或名称列表`；字典改动不回溯已落库的 code）。 */
    public Map<String, Object> displayNames(FormSchema schema, Map<String, Object> values) {
        Map<String, Object> names = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return names;
        }
        for (FormFieldDef field : schema.fields()) {
            Object raw = values.get(field.code());
            if (raw == null) {
                continue;
            }
            if (field.dictType() != null) {
                if (field.type() == com.oa.form.template.schema.FormFieldType.MULTISELECT) {
                    names.put(field.code(), dictService.displayNames(field.dictType(), asStrings(raw)));
                } else {
                    names.put(field.code(), dictService.displayName(field.dictType(), String.valueOf(raw)));
                }
                continue;
            }
            if (field.type() == com.oa.form.template.schema.FormFieldType.SELECT && !field.options().isEmpty()) {
                String code = String.valueOf(raw);
                for (FormFieldDef.Option option : field.options()) {
                    if (option.code().equals(code)) {
                        names.put(field.code(), option.label());
                        break;
                    }
                }
            }
            if (field.type() == com.oa.form.template.schema.FormFieldType.BOOLEAN) {
                // 布尔字段的打印/展示文案（doc/dict-seed.md §6 / §7：勾选 = 计划内 / 本月度）
                names.put(field.code(), Boolean.TRUE.equals(raw) ? "☑ " + field.label() : "☐ " + field.label());
            }
        }
        return names;
    }

    private static List<String> asStrings(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (raw != null) {
            result.add(String.valueOf(raw));
        }
        return result;
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            // 金额/数字一律按**字符串**回读，避免前端把定点数当浮点处理（forms.md §11.5）
            return node.asText();
        }
        if (node.isArray()) {
            List<Object> items = new ArrayList<>();
            node.forEach(item -> items.add(jsonValue(item)));
            return items;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), jsonValue(entry.getValue())));
            return map;
        }
        return node.asText();
    }

    /** 空快照 JSON（新建草稿时 {@code fields_json} 的合法缺省，列 NOT NULL）。 */
    public static String emptyJson() {
        return "{}";
    }
}
