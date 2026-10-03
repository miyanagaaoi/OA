package com.oa.authz.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 字段级限制与导出管控（{@code oa.authz.visibility.*}）的接口出入参。
 *
 * <p>对应契约（normify 基线）：
 * <ul>
 *   <li>{@code GET  /api/v1/authz/field-policy/amount} —— 金额字段对当前角色的读写策略；</li>
 *   <li>{@code GET  /api/v1/authz/field-policy/contact} —— 联系方式脱敏策略；</li>
 *   <li>{@code POST /api/v1/authz/field-policy/assert-write} —— 字段级写入闸门（金额只读的拒绝点）；</li>
 *   <li>{@code POST /api/v1/authz/export-check} —— 导出前鉴权（角色 + 字段范围）；</li>
 *   <li>{@code GET  /api/v1/authz/export-policy} —— 导出策略总览（各目标的生效列）。</li>
 * </ul>
 */
public final class VisibilityDtos {

    private VisibilityDtos() {
    }

    /**
     * 字段级写入预检请求（与表单保存共用同一闸门 {@code FormFieldWriteGuard}）。
     *
     * @param formType      单据类型：{@code matter}/{@code fund}/{@code contract}/{@code seal}，缺省 {@code matter}
     * @param state         单据状态：{@code draft}/{@code approving}/{@code pending_supplement}/{@code closed}，缺省 {@code draft}
     * @param isInitiator   当前登录人是否为本单发起人
     * @param isArchiveNode 当前登录人是否处于节点⑦（归档登记，印鉴单归还字段的唯一例外）
     * @param allFields     该表单的字段全集（草稿态放行全集；缺省取 {@code payload} 的键集）
     * @param payload       提交载荷（键 → 值）；金额字段出现在这里即触发金额只读判定
     * @param strict        {@code true} = 越权即整单拒绝（默认）；{@code false} = 返回过滤后的可写载荷
     */
    public record FieldWriteCheckRequest(
            String formType,
            String state,
            Boolean isInitiator,
            Boolean isArchiveNode,
            List<String> allFields,
            Map<String, Object> payload,
            Boolean strict
    ) {
    }

    /**
     * 字段级写入预检结果。
     *
     * @param allowed           是否整单可写（{@code strict=true} 且被拒时为 {@code false}）
     * @param writableFields    实际可写字段
     * @param rejectedFields    被拒绝的字段（金额只读 → {@code AMOUNT_READ_ONLY}；状态不允许 → {@code FIELD_WRITE_DENIED}）
     * @param amountWritable    当前角色金额是否可写
     * @param filteredPayload   过滤后的载荷（{@code strict=false} 时可用）
     * @param effectiveColumns  （导出预检用）生效导出的列
     */
    public record FieldWriteCheckResponse(
            boolean allowed,
            List<String> writableFields,
            List<RejectedField> rejectedFields,
            boolean amountWritable,
            Map<String, Object> filteredPayload,
            List<String> effectiveColumns
    ) {
    }

    /** 被拒绝的字段及原因（错误码与中文说明）。 */
    public record RejectedField(String field, int code, String reason) {
    }

    /**
     * 导出前鉴权请求（{@code POST /api/v1/authz/export-check}）。
     *
     * @param target 导出目标码：{@code org}/{@code user}/{@code org_leader}/{@code user_position}/
     *               {@code user_role}/{@code instance_list}/{@code audit_log}
     * @param fields 调用方打算导出的列（可空）；含金额列或未授权列 → 403
     */
    public record ExportCheckRequest(
            @NotBlank(message = "导出目标不能为空") String target,
            List<String> fields
    ) {
    }

    /** 导出前鉴权结果。 */
    public record ExportCheckResponse(
            String target,
            boolean allowed,
            boolean masterData,
            List<String> effectiveColumns,
            List<String> excludedFields,
            boolean amountExportEnabled,
            boolean amountExported,
            int code,
            String reason
    ) {
    }
}
