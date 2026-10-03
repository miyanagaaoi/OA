package com.oa.workflow.approver.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批人快照（{@code flow_instance.approver_snapshot_json}）—— **发起时固化、此后不可变**。
 *
 * <p>结构逐字段对齐 doc/data-model.md §7.1（{@code template_version} / {@code parsed_at} /
 * {@code basis} / {@code nodes}），并按 templates.md V-07「节点配置冻结」把节点行为字段一并写入，
 * 使快照同时充当「**发起时的节点配置快照**」——发布新版本（改决议模式/阈值、增删节点）
 * 不会影响在途实例（REQ-FLOW-006 / AC-09）。
 *
 * @param templateId      发起时锁定的模板行 id（{@code flow_instance.template_id}）
 * @param templateCode    单据类型码（matter / fund / contract / seal）
 * @param templateVersion 发起时锁定的版本号（{@code flow_instance.template_version}）
 * @param parsedAt        解析时间（{@code yyyy-MM-dd HH:mm:ss}）
 * @param basis           解析依据（发起人、组织路径、公司、类别、是否涉及费用）
 * @param nodes           逐节点候选人快照（按 seq）
 */
public record ApproverSnapshot(
        Long templateId,
        String templateCode,
        Integer templateVersion,
        String parsedAt,
        Map<String, Object> basis,
        List<SnapshotNode> nodes
) {

    public ApproverSnapshot {
        // 必须容忍 **null 值**：basis 的既定键里 `category` 在一部分单据上是空的
        // （事项单由用户在表单里选、资金/合同/印鉴单可为空），而 `Map.copyOf` 对 null 值抛 NPE
        // （2026-10-04 运行期实测：建草稿 POST /flow-instances {"formType":"matter"} → 50000，
        //  堆栈落在本行）。原来这条路径被「建草稿即预检」挡住不可达：matter 的 ⑤ 依赖 category，
        //  空 category 会在预检处 40007。预检闸门移到提交后，草稿期必须先能固化出「category 为空」
        //  的快照，因此改为 **LinkedHashMap 拷贝 + unmodifiableMap**：保留键序与既有 JSON 契约
        //  （`doc/data-model.md` §7.1 的 basis 六键仍在，空值为 null），只放弃「非空值」这条额外约束。
        basis = basis == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(basis));
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    /**
     * 单节点快照。
     *
     * @param nodeSeq           节点序号
     * @param nodeCode          节点码
     * @param nodeName          节点名称
     * @param nodeType          节点类型（approve / cc / archive）
     * @param rule              解析规则码
     * @param decisionMode      发起时冻结的决议模式
     * @param passThreshold     发起时冻结的通过阈值
     * @param signPolicy        发起时冻结的签名策略
     * @param timeoutHours      发起时冻结的超时时长（仅催办）
     * @param timeoutCcSuperior 超时是否抄送上级
     * @param allowAddSign      是否允许加签
     * @param allowJump         是否允许自由跳转
     * @param allowRoute        是否允许流转/回退
     * @param skipped           是否因跳过条件被跳过
     * @param skipReason        跳过原因
     * @param evidence          解析证据链
     * @param approvers         候选人（含姓名/工号/组织）
     * @param groups            候选人多组（协同部门独立会签；元素为用户 id）
     * @param requiredApprovals 通过所需同意人数（或签为 1；跳过/登记为 0）
     * @param thresholdBasis    阈值判定依据（any / absolute / percent / majority）
     * @param satisfiable       候选人是否足以达到阈值
     */
    public record SnapshotNode(
            Integer nodeSeq,
            String nodeCode,
            String nodeName,
            String nodeType,
            String rule,
            String decisionMode,
            String passThreshold,
            String signPolicy,
            Integer timeoutHours,
            Boolean timeoutCcSuperior,
            Boolean allowAddSign,
            Boolean allowJump,
            Boolean allowRoute,
            Boolean skipped,
            String skipReason,
            String evidence,
            List<SnapshotApprover> approvers,
            List<List<Long>> groups,
            Integer requiredApprovals,
            String thresholdBasis,
            Boolean satisfiable
    ) {

        public SnapshotNode {
            approvers = approvers == null ? List.of() : List.copyOf(approvers);
            groups = groups == null ? List.of() : List.copyOf(groups);
        }

        /** 是否阻断（未跳过且无候选人）。 */
        public boolean blocker() {
            return !Boolean.TRUE.equals(skipped) && (approvers == null || approvers.isEmpty());
        }
    }

    /**
     * 快照中的单条候选人（doc/data-model.md §7.1 的 {@code approvers[]} 超集：
     * 额外带 {@code employee_no} 与 {@code org_path}，满足「姓名 / 工号 / 组织」的固化要求）。
     */
    public record SnapshotApprover(
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
}
