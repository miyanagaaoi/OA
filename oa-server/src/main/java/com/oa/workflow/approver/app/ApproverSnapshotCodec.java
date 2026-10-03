package com.oa.workflow.approver.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.oa.workflow.approver.domain.ApproverSnapshot;
import com.oa.workflow.approver.domain.ApproverSnapshot.SnapshotApprover;
import com.oa.workflow.approver.domain.ApproverSnapshot.SnapshotNode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批人快照的序列化 / 反序列化 —— <b>纯函数</b>。
 *
 * <p>用 {@link JsonText} 的**专用 ObjectMapper**（不是 Spring 的 Web mapper）：
 * Web mapper 把 {@code Long} 写成字符串（主键防 JS 精度丢失），
 * 若拿它落库，快照里的 {@code user_id} 会变成 {@code "2001"}，与
 * doc/data-model.md §7.1 的结构以及 MySQL {@code JSON_EXTRACT} 的数字口径漂移。
 *
 * <p>往返是**有损可控**的：解析回读时会保留全部业务字段；
 * 但反序列化只用于「重新解析前读取旧快照留审计」与出参展示，**不参与运行时判定**
 * （运行时权威是数据库里那一份 JSON 文本本身）。
 */
public final class ApproverSnapshotCodec {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 快照专用 ObjectMapper：**snake_case 键名**（doc/data-model.md §7.1 的结构契约）。
     *
     * <p>为什么不能复用 {@link JsonText} 的默认 mapper：Jackson 默认按 Java 属性名输出驼峰
     * （{@code parsedAt} / {@code nodeSeq}），而 §7.1 的契约是 {@code parsed_at} / {@code node_seq}。
     * 也不能用 Spring 的 Web mapper：它把 {@code Long} 写成字符串（主键防 JS 精度丢失），
     * 会让 {@code user_id} 变成 {@code "2001"}，与 {@code JSON_EXTRACT} 的数字口径漂移。
     */
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private ApproverSnapshotCodec() {
    }

    /** 把解析结果组装成快照对象（尚未序列化）。 */
    public static ApproverSnapshot assemble(Long templateId, String templateCode, Integer templateVersion,
                                            RuleRequest request, List<ApproverResolutionService.NodeResolution> nodes) {
        return new ApproverSnapshot(
                templateId,
                templateCode,
                templateVersion,
                LocalDateTime.now().format(TIME),
                basis(request),
                toNodes(nodes));
    }

    /** 快照的 {@code basis}（doc/data-model.md §7.1）。 */
    public static Map<String, Object> basis(RuleRequest request) {
        Map<String, Object> basis = new LinkedHashMap<>();
        if (request == null) {
            return basis;
        }
        basis.put("initiator_id", request.initiatorId());
        basis.put("initiator_org_id", request.initiatorOrgId());
        basis.put("initiator_org_path", request.initiatorOrgPath());
        basis.put("company_id", request.initiatorCompanyId());
        basis.put("category", request.category());
        basis.put("involve_cost", request.involveCost());
        if (!request.initiatorPicks().isEmpty()) {
            basis.put("initiator_picks", request.initiatorPicks());
        }
        return basis;
    }

    /** 逐节点转换。 */
    public static List<SnapshotNode> toNodes(List<ApproverResolutionService.NodeResolution> resolutions) {
        List<SnapshotNode> nodes = new ArrayList<>();
        if (resolutions == null) {
            return nodes;
        }
        for (ApproverResolutionService.NodeResolution resolution : resolutions) {
            if (resolution == null || resolution.node() == null) {
                continue;
            }
            NodeConfig node = resolution.node();
            List<SnapshotApprover> approvers = new ArrayList<>();
            for (Candidate candidate : resolution.candidates()) {
                approvers.add(new SnapshotApprover(candidate.userId(), candidate.name(), candidate.account(),
                        candidate.employeeNo(), candidate.orgId(), candidate.orgName(), candidate.orgPath(),
                        candidate.companyId(), candidate.position()));
            }
            List<List<Long>> groups = new ArrayList<>();
            for (List<Candidate> group : resolution.groups()) {
                List<Long> ids = new ArrayList<>();
                for (Candidate candidate : group) {
                    ids.add(candidate.userId());
                }
                groups.add(ids);
            }
            nodes.add(new SnapshotNode(
                    node.seq(), node.nodeCode(), node.name(), node.nodeType(), node.approverRule(),
                    node.decisionMode(), node.passThreshold(), node.signPolicy(), node.timeoutHours(),
                    node.timeoutCcSuperior(), node.allowAddSign(), node.allowJump(), node.allowRoute(),
                    resolution.skipped(), resolution.skipReason(), resolution.evidence(),
                    approvers, groups, resolution.requiredApprovals(),
                    resolution.threshold() == null ? null : resolution.threshold().basis(),
                    resolution.threshold() == null ? null : resolution.threshold().satisfiable()));
        }
        return nodes;
    }

    /** 快照 → JSON 文本（落库）。 */
    public static String write(ApproverSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalArgumentException("审批人快照序列化失败：" + ex.getMessage());
        }
    }

    /** JSON 文本 → 快照（读回；非法 JSON 抛 {@link IllegalArgumentException}）。 */
    public static ApproverSnapshot read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return SNAPSHOT_MAPPER.readValue(json, ApproverSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("审批人快照反序列化失败：" + ex.getMessage());
        }
    }

    /**
     * 快照中的候选人用户 id 集合（**去重、保序**）。
     *
     * <p>用途：{@code flow_node_instance.approver_ids_json} 的派生源
     * （doc/data-model.md §7.1「实现要求」第 2 条：节点实例的候选人**从快照派生**）。
     */
    public static List<Long> approverIds(ApproverSnapshot snapshot, String nodeCode) {
        List<Long> ids = new ArrayList<>();
        if (snapshot == null || snapshot.nodes() == null) {
            return ids;
        }
        for (SnapshotNode node : snapshot.nodes()) {
            if (nodeCode == null || nodeCode.equals(node.nodeCode())) {
                for (SnapshotApprover approver : node.approvers()) {
                    if (approver.userId() != null && !ids.contains(approver.userId())) {
                        ids.add(approver.userId());
                    }
                }
            }
        }
        return ids;
    }

    /** 是否存在缺候选人的节点（读回后的自检；用于诊断历史数据）。 */
    public static boolean hasBlocker(ApproverSnapshot snapshot) {
        if (snapshot == null || snapshot.nodes() == null) {
            return false;
        }
        for (SnapshotNode node : snapshot.nodes()) {
            if (node.blocker()) {
                return true;
            }
        }
        return false;
    }
}
