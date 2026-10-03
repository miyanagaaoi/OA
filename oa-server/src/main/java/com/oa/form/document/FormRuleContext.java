package com.oa.form.document;

import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.validate.ValidationMode;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 四类单据**专属规则**的输入上下文（{@code oa.form.{matter,fund,contract,seal}}）。
 *
 * @param schema    本单 schema（**实例锁定版本**，见 {@code FormSchemaService#forInstance}）
 * @param values    **合并后**的字段值（落库前最终态：已存值 ⊕ 本次提交值 ⊕ 模板默认值）——
 *                  条件必填与跨字段规则必须在最终态上判定，否则「分两次保存」能绕过校验
 * @param incoming  本次提交**实际携带**的字段码（判定「是否试图改动」用它，别用 {@code values}）
 * @param stored    变更前已落库的值（判定「类别是否被改判」「归还状态是否变化」用它）
 * @param mode      校验档（{@link ValidationMode}）
 * @param instance  流程实例（新建草稿前的干跑校验时为 {@code null}）
 * @param today     「今天」的口径（日期规则与时限判定）
 */
public record FormRuleContext(
        FormSchema schema,
        Map<String, Object> values,
        Set<String> incoming,
        Map<String, Object> stored,
        ValidationMode mode,
        FlowInstanceRow instance,
        LocalDate today
) {

    public FormRuleContext {
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
        stored = stored == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(stored));
        incoming = incoming == null ? Set.of() : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(incoming));
    }

    /** 取字段值（缺失返回 {@code null}）。 */
    public Object value(String code) {
        return values.get(code);
    }

    /** 落库前的值（缺失返回 {@code null}）。 */
    public Object storedValue(String code) {
        return stored.get(code);
    }

    /** 本次提交是否携带了该字段。 */
    public boolean touched(String code) {
        return incoming.contains(code);
    }

    /** 是否草稿态（实例为空按草稿处理：建草稿前的干跑）。 */
    public boolean draftState() {
        return instance == null || "draft".equalsIgnoreCase(String.valueOf(instance.getStatus()));
    }
}
