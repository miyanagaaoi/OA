package com.oa.workflow.approver.api.dto;

import com.oa.workflow.approver.domain.ApproverSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 审批人解析 / 快照 / 发起前预检接口的出参与入参
 * （{@code /api/v1/approver-rules/**}、{@code /api/v1/flow-instances/**}）。
 */
public final class ApproverDtos {

    private ApproverDtos() {
    }

    // ================================================================ 规则清单

    /** 规则清单项（9 条，逐条给出出处章节）。 */
    public record RuleView(
            String rule,
            String label,
            String source,
            boolean trunkUsable,
            boolean requiresParam
    ) {
    }

    /** 候选人（含姓名 / 工号 / 组织）。 */
    public record CandidateView(
            Long userId,
            String name,
            String account,
            String employeeNo,
            Long orgId,
            String orgName,
            String orgPath,
            Long companyId,
            String position
    ) {
    }

    /** 单规则解析请求。 */
    public record RuleResolveRequest(
            Long initiatorId,
            Long initiatorOrgId,
            Long initiatorCompanyId,
            String category,
            List<Long> initiatorPicks,
            List<Long> collabDeptIds,
            /** 协同勾选时必须排除的部门（②所属部门自身，AC-05）。 */
            List<Long> collabSelfExcludeDeptIds,
            Map<String, Object> formValues,
            /** {@code designated} 的声明参数（{@code {"user_ids":[...]}} / {@code {"role_code":"..."}}）。 */
            com.fasterxml.jackson.databind.JsonNode approverParam,
            /** 可选：直接用某模板的某节点配置解析（校验设计期配置）。 */
            Long templateId,
            Integer nodeSeq
    ) {
    }

    /** 单规则解析结果。 */
    public record RuleResolveView(
            String rule,
            String label,
            String source,
            boolean resolved,
            String evidence,
            List<CandidateView> candidates,
            List<List<CandidateView>> groups,
            List<String> missingConfig
    ) {
    }

    /** 去重策略演示请求（同一节点内的候选人清单，可含重复）。 */
    public record DedupRequest(List<Long> userIds) {
    }

    /** 去重策略演示结果。 */
    public record DedupView(int total, int kept, List<CandidateView> candidates,
                            List<CandidateView> removed, String evidence) {
    }

    /** 连续节点合并策略视图。 */
    public record MergePolicyView(boolean mergeConsecutiveNodes, String description) {
    }

    /** 财务部负责人健康度（空缺预警：为空则任何单据都发不出去）。 */
    public record FinanceHealthView(
            String configuredName,
            Long configuredId,
            Long orgId,
            String orgName,
            String orgPath,
            int leaderCount,
            List<CandidateView> leaders,
            boolean healthy,
            String message
    ) {
    }

    // ================================================================ 发起前预检

    /**
     * 预检 / 建实例的公共入参。
     *
     * <p>{@code initiatorId} 为空表示「以当前登录人为发起人」；显式传入用于管理员代查
     * （例如排查「某员工为什么发不出去」）。
     */
    public record PrecheckRequest(
            Long templateId,
            /** 可选：指定版本（默认取当前 published 版本）。 */
            Integer templateVersion,
            Long initiatorId,
            String formType,
            String category,
            Boolean involveCost,
            List<Long> initiatorPicks,
            List<Long> collabDeptIds,
            List<Long> collabSelfExcludeDeptIds,
            Map<String, Object> formValues
    ) {
    }

    /** 预检中的单节点结论。 */
    public record PrecheckNodeView(
            Integer nodeSeq,
            String nodeCode,
            String nodeName,
            String nodeType,
            String rule,
            boolean skipped,
            String skipReason,
            boolean blocker,
            List<CandidateView> candidates,
            List<List<CandidateView>> groups,
            int requiredApprovals,
            String thresholdBasis,
            Boolean satisfiable,
            String evidence,
            List<String> missingConfig
    ) {
    }

    /** 拦截项：**哪个节点、命中哪条规则、缺什么配置**（AC-11 / AC-19 的关键）。 */
    public record PrecheckBlockerView(
            Integer nodeSeq,
            String nodeCode,
            String nodeName,
            String rule,
            String ruleLabel,
            String reason,
            List<String> missingConfig
    ) {
    }

    /** 预检报告。 */
    public record PrecheckReportView(
            boolean allowed,
            Long templateId,
            String templateCode,
            Integer templateVersion,
            Long initiatorId,
            String initiatorName,
            List<PrecheckBlockerView> blockers,
            List<PrecheckNodeView> nodes,
            List<String> warnings
    ) {
    }

    /** 拦截规则清单项。 */
    public record PrecheckRuleView(String rule, String description) {
    }

    // ================================================================ 实例

    /** 建实例请求（在预检入参之上加单号与表单字段）。 */
    public record CreateInstanceRequest(
            Long templateId,
            Integer templateVersion,
            /** 发起人：为空 = 当前登录人；显式传入供管理员代发起与排障（口径同预检）。 */
            Long initiatorId,
            @Size(max = 32, message = "单号不得超过 32 字符") String bizNo,
            String formType,
            String category,
            Boolean involveCost,
            List<Long> initiatorPicks,
            List<Long> collabDeptIds,
            List<Long> collabSelfExcludeDeptIds,
            Map<String, Object> formValues,
            Map<String, Object> fields
    ) {
    }

    /** 实例视图（含锁定版本与快照）。 */
    public record InstanceView(
            Long id,
            String bizNo,
            Long templateId,
            Integer templateVersion,
            String templateCode,
            String formType,
            String category,
            Long initiatorId,
            Long initiatorOrgId,
            Long initiatorCompanyId,
            String initiatorOrgPath,
            String status,
            String subStatus,
            Integer currentNodeSeq,
            Long ownerDeptId,
            String submittedAt,
            String createdAt,
            ApproverSnapshot snapshot
    ) {
    }

    /** 重新解析快照请求（驳回重提；旧快照留审计）。 */
    public record ReparseRequest(@Size(max = 255, message = "原因不得超过 255 字符") String reason) {
    }

    /** 快照重新解析结果。 */
    public record ReparseView(
            Long instanceId,
            Integer previousTemplateVersion,
            Integer templateVersion,
            boolean allowed,
            List<PrecheckBlockerView> blockers,
            String message
    ) {
    }

    // ================================================================ 审计

    /** 通用消息响应。 */
    public record MessageView(String message) {
    }

    /** 提交请求（草稿 → 审批中；状态机其余迁移属 2a.4）。 */
    public record SubmitRequest(@NotBlank(message = "提交必须给出原因/说明（≤255 字）")
                                @Size(max = 255, message = "说明不得超过 255 字符") String reason) {
    }
}
