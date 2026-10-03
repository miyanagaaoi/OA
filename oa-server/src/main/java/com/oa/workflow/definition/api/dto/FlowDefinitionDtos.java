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
 * 用于 PUT 全量覆盖（与 {@code 04-permissions} 的 PUT 语义一致）—— 适用字段仅限
 * {@code passThreshold} / {@code skipCondition} / {@code approverParam} 三个「有独立空值含义」的字段，
 * 逐个的三态写在各自的 record 注释里（2026-10-04 统一为 {@code null} = 清空）。
 */
public final class FlowDefinitionDtos {

    private FlowDefinitionDtos() {
    }

    // ================================================================ Q6 / Q7 闸门配置

    /**
     * Q6 / Q7 闸门配置 + 撤回窗口（模板级；2026-10-03 裁定 Q6/Q7 为可配置项，
     * 2026-10-04 裁定**撤回窗口**为可配置项）。
     *
     * <p>键位与语义见 {@code doc/templates.md} §1.7 与 §1.8；{@code null}/{@code 0} 次数 = 不限，
     * {@code null} 天数 = 不设时限，{@code withdrawWindow} 的 {@code null} = 取默认
     * {@code until_finance_approved}（REQ-FLOW-009 口径）。
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
            /**
             * 撤回窗口口径（**回显的是生效值**，永不为 {@code null}）：
             * {@code until_finance_approved} ②通过前（含②审批中，默认，REQ-FLOW-009 口径）/
             * {@code until_finance_started} ②开始前（AC-16 严格口径）。
             */
            String withdrawWindow,
            /** 撤回窗口是否被显式配置过（{@code false} = 列为 NULL、取默认口径）。 */
            boolean withdrawWindowConfigured,
            /** 次数是否不限（含补件无时限）——便于前端直接渲染「不限」。 */
            boolean unlimited,
            /** 是否等价于 V0.4 定稿默认值（5 / 3 / 3 工作日 / notify / 撤回窗口取默认）。 */
            boolean v04Default
    ) {
    }

    /**
     * 闸门配置写入请求（PUT）；字段可空，{@code null} 即「不限 / 不设时限 / 撤回窗口取默认」。
     *
     * <p><b>纯追加</b>：{@code withdrawWindow} 为 2026-10-04 新增，既有 5 个字段的语义与校验完全不变；
     * 非法枚举 → 400 / {@code 40008}（与同入口两个既有枚举逐字同口径）。
     */
    public record GatePolicyRequest(
            @Min(value = 0, message = "回退次数上限不得为负数（0 或留空 = 不限）")
            Integer maxReturnCount,
            @Min(value = 0, message = "补件次数上限不得为负数（0 或留空 = 不限）")
            Integer maxSupplementCount,
            @Min(value = 1, message = "补件时限天数必须 ≥1（不设时限请留空）")
            Integer supplementDeadlineDays,
            String supplementDeadlineType,
            String onSupplementTimeout,
            /**
             * 撤回窗口口径（可空 = 取默认 {@code until_finance_approved}）：
             * {@code until_finance_approved} / {@code until_finance_started}；其余值一律 400。
             */
            String withdrawWindow
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

    /**
     * 节点写入请求（新增 / 全量更新共用）。
     *
     * <p><b>可空语义（2026-10-04 统一）</b>：三个「可空 = 清空」的字段
     * —— {@code passThreshold} / {@code skipCondition} / {@code approverParam}
     * —— 省略（{@code null}）即**清空**对应列，与两个专用 PUT 端点
     * （{@code PUT /flow-nodes/{id}/decision|skip-condition|approver-rule}）逐字同口径；
     * 其余标量字段省略 = 保持原值（见 {@code FlowDefinitionService#applyRequest}）。
     *
     * <p><b>{@code seq} 的两态</b>：显式给出 → 插入到该位置并顺延后续节点；
     * 省略 → **追加到末尾**（落库 = 当前最大 seq + 1）。
     */
    public record NodeRequest(
            /** 插入位置；省略 = 追加到末尾（seq = 当前最大 + 1）；主干节点码恒用其固定 seq。 */
            Integer seq,
            @NotBlank(message = "node_code 不能为空")
            @Size(max = 32, message = "node_code 不得超过 32 字符") String nodeCode,
            @Size(max = 50, message = "节点名称不得超过 50 字符") String name,
            String nodeType,
            @NotBlank(message = "approver_rule 不能为空") String approverRule,
            /** 解析规则参数；{@code null} = 清空（与 {@code skipCondition} / {@code passThreshold} 一致）。 */
            JsonNode approverParam,
            String decisionMode,
            /**
             * 阈值字面量（{@code "2"} / {@code "50%"}）——<b>三态，逐字</b>：
             * <ol>
             *   <li>{@code null}（省略或显式 JSON {@code null}）→ <b>清空</b>（落 {@code NULL}）；</li>
             *   <li>空串 / 全空白串 → <b>清空</b>（与 {@code null} 同义）；</li>
             *   <li>非空字面量 → <b>写入</b>（去首尾空白）。</li>
             * </ol>
             * {@code thresholdAbsolute} / {@code thresholdPercent} 任一非空时优先走它们（T-07）。
             */
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
            /** 跳过条件；{@code null} = 清空（与 {@code approverParam} / {@code passThreshold} 一致）。 */
            JsonNode skipCondition
    ) {
    }

    /** 节点换序请求：给出目标顺序的节点 id 列表（必须覆盖当前模板的全部节点）。 */
    public record NodeOrderRequest(List<Long> nodeIds) {
    }

    /**
     * 决议模式写入请求（{@code PUT /flow-nodes/{id}/decision}）。
     *
     * <p>{@code passThreshold} 的三态与 {@code NodeRequest#passThreshold} <b>逐字一致</b>
     * （{@code null} = 清空 / 空串 = 清空 / 非空字面量 = 写入）；改前本端点是
     * 「{@code null} = 不改动、空串 = 清空」，是唯一与同族字段相反的入口（2026-10-04 统一）。
     * 若只想改决议模式而保留阈值，请**显式回传**当前阈值字面量（前端已如此下发）。
     */
    public record NodeDecisionRequest(
            String decisionMode,
            /**
             * 阈值字面量（{@code "2"} / {@code "50%"}）——<b>三态，逐字</b>：
             * <ol>
             *   <li>{@code null}（省略或显式 JSON {@code null}）→ <b>清空</b>（落 {@code NULL}）；</li>
             *   <li>空串 / 全空白串 → <b>清空</b>（与 {@code null} 同义）；</li>
             *   <li>非空字面量 → <b>写入</b>（去首尾空白）。</li>
             * </ol>
             * {@code thresholdAbsolute} / {@code thresholdPercent} 任一非空时优先走它们（T-07）。
             */
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

    /** 跳过条件写入请求（{@code null} = 清空；与 {@code NodeRequest#skipCondition} 逐字一致）。 */
    public record NodeSkipConditionRequest(JsonNode skipCondition) {
    }

    /**
     * 解析规则写入请求。
     *
     * <p>{@code approverRule} 必填；{@code approverParam} 的 {@code null} = <b>清空</b>
     * （与 {@code skipCondition} / {@code passThreshold} 同口径）。
     */
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

    /**
     * 在途实例锁版本视图（{@code GET /flow-templates/{id}/locked-by}）—— 便于管理员确认「改模板不影响谁」
     * （AC-09：「哪些在途实例锁着这个版本」）。
     *
     * <p><b>可见性</b>：行集在**调用人数据域**下产生（{@code flow_instance} 的 SELECT 带
     * {@code @dataScope} 标记、Mapper 不继承 {@code BaseMapper}）—— 域外实例根本不出现在结果里，
     * 而不是返回后再过滤。前 4 个字段是原契约（{@code oa-web} 的 {@code WireLockedInstanceView}），
     * 后 5 个为 2026-10-04 补齐（纯追加，旧调用方不受影响）。
     */
    public record LockedInstanceView(
            Long instanceId,
            String bizNo,
            Integer templateVersion,
            String status,
            /** 发起人 id（数据域内的单据才有行）。 */
            Long initiatorId,
            /** 发起人姓名（{@code sys_user.name}；用户已删除时为 {@code null}）。 */
            String initiatorName,
            /** 当前节点序号（实例主状态 {@code approving} 时的当前节点）。 */
            Integer currentNodeSeq,
            /** 当前节点名（取该实例当前 seq 的节点实例名；节点实例缺失时为 {@code null}）。 */
            String currentNodeName,
            /** 发起（首次提交）时间，格式 {@code yyyy-MM-dd HH:mm:ss}；未提交时取创建时间。 */
            String submittedAt
    ) {
    }
}
