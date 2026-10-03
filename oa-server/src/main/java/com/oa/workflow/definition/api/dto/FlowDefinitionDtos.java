package com.oa.workflow.definition.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 流程模板 / 节点定义接口的出参与入参（{@code /api/v1/flow-templates/**}、{@code /flow-nodes/**}、
 * {@code /flow-designs/**}）。
 *
 * <p>字段命名与 {@code doc/templates.md} §1 的配置表逐列对应；入参的「可空 = 清空」语义
 * 用于 PUT 全量覆盖（与 {@code 04-permissions} 的 PUT 语义一致）。
 */
public final class FlowDefinitionDtos {

    private FlowDefinitionDtos() {
    }

    // ================================================================ Q6 / Q7 闸门配置

    /**
     * Q6 / Q7 闸门配置（模板级；2026-10-03 产品裁定：**两者均为可配置项**）。
     *
     * <p>键位与语义见 {@code doc/templates.md} §1.7；{@code null}/{@code 0} 次数 = 不限，
     * {@code null} 天数 = 不设时限。
     */
    public record GatePolicyView(
            /** 回退次数上限（null/0 = 不限）。 */
            Integer maxReturnCount,
            /** 补件次数上限（null/0 = 不限）。 */
            Integer maxSupplementCount,
            /** 补件时限天数（null = 不设时限）。 */
            Integer supplementDeadlineDays,
            /** 补件时限口径：calendar 自然日 / working 工作日。 */
            String supplementDeadlineType,
            /** 超时处理：notify 仅提醒 / auto_pass 自动通过 / auto_return 自动退回。 */
            String onSupplementTimeout,
            /** 次数是否不限（含补件无时限）——便于前端直接渲染「不限」。 */
            boolean unlimited,
            /** 是否等价于 V0.4 定稿默认值（5 / 3 / 3 工作日 / notify）。 */
            boolean v04Default
    ) {
    }

    /** 闸门配置写入请求（PUT）；字段可空，{@code null} 即「不限 / 不设时限」。 */
    public record GatePolicyRequest(
            @Min(value = 0, message = "回退次数上限不得为负数（0 或留空 = 不限）")
            Integer maxReturnCount,
            @Min(value = 0, message = "补件次数上限不得为负数（0 或留空 = 不限）")
            Integer maxSupplementCount,
            @Min(value = 1, message = "补件时限天数必须 ≥1（不设时限请留空）")
            Integer supplementDeadlineDays,
            String supplementDeadlineType,
            String onSupplementTimeout
    ) {
    }

    // ================================================================ 模板

    /** 模板列表项 / 详情（含 Q6/Q7 闸门配置）。 */
    public record TemplateView(
            Long id,
            String code,
            String name,
            String formType,
            Integer version,
            String status,
            Integer nodeCount,
            String publishedAt,
            String createdAt,
            String updatedAt,
            GatePolicyView gatePolicy,
            /** 是否可被新实例使用（仅 published 为 true）。 */
            boolean usableByNewInstance,
            /** 是否只读（published / archived）。 */
            boolean readOnly
    ) {
    }

    /** 模板详情（含节点清单）。 */
    public record TemplateDetailView(TemplateView template, List<NodeView> nodes) {
    }

    /** 节点配置视图。 */
    public record NodeView(
            Long id,
            Long templateId,
            Integer seq,
            String nodeCode,
            String nodeCodeLabel,
            String name,
            String nodeType,
            String approverRule,
            String approverRuleLabel,
            JsonNode approverParam,
            String decisionMode,
            String passThreshold,
            /** 阈值的人类可读解析（如「过半（3 人候选 → 2 人同意）」由 resolve 接口给出，这里只给字面量语义）。 */
            String thresholdDescription,
            String signPolicy,
            Integer timeoutHours,
            Boolean timeoutCcSuperior,
            Boolean allowAddSign,
            Boolean allowJump,
            Boolean allowRoute,
            JsonNode skipCondition,
            String createdAt,
            String updatedAt
    ) {
    }

    /** 开新版本请求（基于某已发布版本开草稿，templates.md §4.1 第 4 步）。 */
    public record NewVersionRequest(
            /** 源版本号；为空则取当前已发布版本。 */
            Integer fromVersion,
            /** 新版本名称；为空则沿用源版本名称。 */
            @Size(max = 80, message = "模板名称不得超过 80 字符") String name
    ) {
    }

    /** 发布 / 归档请求（原因可选，便于管理后台留痕）。 */
    public record PublishRequest(@Size(max = 255, message = "原因不得超过 255 字符") String reason) {
    }

    /** 模板元数据更新请求（仅草稿可写）。 */
    public record TemplateUpdateRequest(
            @Size(max = 80, message = "模板名称不得超过 80 字符") String name,
            JsonNode formSchemaJson,
            GatePolicyRequest gatePolicy
    ) {
    }

    // ================================================================ 节点写

    /** 节点写入请求（新增 / 全量更新共用；PUT 语义 = 传 null 即清空）。 */
    public record NodeRequest(
            Integer seq,
            @NotBlank(message = "node_code 不能为空")
            @Size(max = 32, message = "node_code 不得超过 32 字符") String nodeCode,
            @Size(max = 50, message = "节点名称不得超过 50 字符") String name,
            String nodeType,
            @NotBlank(message = "approver_rule 不能为空") String approverRule,
            JsonNode approverParam,
            String decisionMode,
            /** 阈值字面量（"2" / "50%"）；与 thresholdAbsolute/thresholdPercent 三选一。 */
            String passThreshold,
            /** 绝对人数阈值（与 percent 同时给出时**绝对人数优先**，templates.md T-07）。 */
            Integer thresholdAbsolute,
            Integer thresholdPercent,
            String signPolicy,
            Integer timeoutHours,
            Boolean timeoutCcSuperior,
            Boolean allowAddSign,
            Boolean allowJump,
            Boolean allowRoute,
            JsonNode skipCondition
    ) {
    }

    /** 节点换序请求：给出目标顺序的节点 id 列表（必须覆盖当前模板的全部节点）。 */
    public record NodeOrderRequest(List<Long> nodeIds) {
    }

    /** 决议模式写入请求。 */
    public record NodeDecisionRequest(
            String decisionMode,
            String passThreshold,
            Integer thresholdAbsolute,
            Integer thresholdPercent
    ) {
    }

    /** 决议模式解析结果（{@code POST /flow-nodes/{id}/decision/resolve}）。 */
    public record DecisionResolveView(
            Long nodeId,
            String decisionMode,
            String passThreshold,
            Integer candidateCount,
            int requiredApprovals,
            String basis,
            boolean satisfiable,
            String description
    ) {
    }

    /** 签名策略 / 超时 / 开关写入请求。 */
    public record NodePolicyRequest(
            String signPolicy,
            Integer timeoutHours,
            Boolean timeoutCcSuperior,
            Boolean allowAddSign,
            Boolean allowJump,
            Boolean allowRoute
    ) {
    }

    /** 跳过条件写入请求（{@code null} = 清空）。 */
    public record NodeSkipConditionRequest(JsonNode skipCondition) {
    }

    /** 解析规则写入请求。 */
    public record NodeApproverRuleRequest(String approverRule, JsonNode approverParam) {
    }

    /** 校验结论（写接口的即时校验与发布前 dry-run 共用）。 */
    public record ValidationView(boolean passed, List<String> problems, List<String> warnings) {
    }

    // ================================================================ 发布前校验

    /** 单条校验规则结论。 */
    public record CheckItemView(String rule, String title, String status, List<String> details) {
    }

    /** 发布前 dry-run 报告。 */
    public record PrePublishReportView(
            Long templateId,
            String code,
            Integer version,
            String status,
            boolean passed,
            String generatedAt,
            List<CheckItemView> checks,
            List<String> problems,
            List<String> warnings
    ) {
    }

    /** 规则清单项（{@code GET /flow-designs/check-rules}）。 */
    public record CheckRuleView(String rule, String title) {
    }

    /** 在途实例锁版本视图（{@code GET /flow-templates/{id}/locked-by}）—— 便于管理员确认「改模板不影响谁」。 */
    public record LockedInstanceView(Long instanceId, String bizNo, Integer templateVersion, String status) {
    }
}
