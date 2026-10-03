package com.oa.form.template.writemodel;

import com.oa.form.app.FormWritePolicy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 写入上下文 —— 三态白名单判定需要的**全部输入**（一次算清，避免调用方各自拼装）。
 *
 * @param state         单据状态（由 {@code flow_instance.status + sub_status} 推导）
 * @param formType      单据类型（四类之一）
 * @param isInitiator   调用人是否为本单发起人（印鉴单归还登记的例外主体之一）
 * @param isArchiveNode 调用人是否处于节点⑦（{@code archive_register}）且是该节点的候选人
 *                      （例外主体之二）
 * @param allFields     该 schema 的字段全集（草稿态直接返回它）
 * @param attachmentFields 该 schema 里 {@code type ∈ {file, files}} 的字段码（待补件窗口按**字段类型**
 *                      放行附件类字段的输入；空集 = 调用方未提供类型信息，退化为系统字段码清单）
 * @param writableFields 可写字段集合（由 {@link FormWritePolicy} 计算，此处缓存一份便于出参）
 */
public record WriteContext(
        FormWritePolicy.FormState state,
        FormWritePolicy.FormType formType,
        boolean isInitiator,
        boolean isArchiveNode,
        Set<String> allFields,
        Set<String> attachmentFields,
        Set<String> writableFields
) {

    public WriteContext {
        allFields = allFields == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(allFields));
        attachmentFields = attachmentFields == null
                ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(attachmentFields));
        writableFields = writableFields == null
                ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(writableFields));
    }

    /** 计算上下文（纯函数：{@link FormWritePolicy} 是唯一判定源；**无** schema 字段类型信息）。 */
    public static WriteContext of(FormWritePolicy.FormState state, FormWritePolicy.FormType formType,
                                  boolean isInitiator, boolean isArchiveNode, Set<String> allFields) {
        return of(state, formType, isInitiator, isArchiveNode, allFields, Set.of());
    }

    /**
     * 计算上下文（纯函数）：把 schema 附件类字段一并喂给 {@link FormWritePolicy}，
     * 使待补件窗口按**字段类型**放行全部附件类字段（{@code doc/forms.md} §1.2）。
     */
    public static WriteContext of(FormWritePolicy.FormState state, FormWritePolicy.FormType formType,
                                  boolean isInitiator, boolean isArchiveNode, Set<String> allFields,
                                  Set<String> attachmentFields) {
        Set<String> writable = FormWritePolicy.writableFields(state, formType, isInitiator, isArchiveNode, allFields,
                attachmentFields);
        return new WriteContext(state, formType, isInitiator, isArchiveNode, allFields, attachmentFields, writable);
    }

    /** 某字段是否可写。 */
    public boolean writable(String fieldCode) {
        return fieldCode != null && writableFields.contains(fieldCode);
    }

    /** 出参视图（前端置灰提示用；**不是**边界，边界在服务端断言）。 */
    public Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("state", state.name());
        view.put("formType", formType.name());
        view.put("isInitiator", isInitiator);
        view.put("isArchiveNode", isArchiveNode);
        view.put("writableFields", writableFields);
        view.put("attachmentFields", attachmentFields);
        view.put("readonlyFields", allFields.stream().filter(code -> !writableFields.contains(code)).toList());
        view.put("stateLabel", label(state));
        view.put("evidence", "doc/forms.md §1.2 字段的三态读写模型（草稿全部可写 / 审批中无 / "
                + "待补件仅**附件类字段（type=file/files）**与补件说明）");
        return view;
    }

    private static String label(FormWritePolicy.FormState state) {
        return switch (state) {
            case DRAFT -> "草稿（全部可写）";
            case APPROVING -> "审批中（全部只读，仅印鉴单归还状态/日期对发起人与节点⑦例外）";
            case PENDING_SUPPLEMENT -> "待补件（仅附件类字段与补件说明可写；归还状态/日期同样只读）";
            case CLOSED -> "已完结（只读）";
        };
    }
}
