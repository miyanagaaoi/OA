package com.oa.workflow.definition.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeCode;
import com.oa.workflow.definition.domain.FlowNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「必填节点不可删」与「主干顺序固定」的判定 —— <b>纯函数</b>。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/templates.md §1.0「主干节点数：<b>固定 7 个</b>（①–⑦），<b>顺序固定</b>」；</li>
 *   <li>doc/enums.md §2「主干节点<b>固定 7 个、顺序固定</b>；一期无条件路由」；</li>
 *   <li>doc/prd-0.1.md §6.3 主干审批链（V0.4 定稿，REQ-FLOW-025）。</li>
 * </ul>
 *
 * <p><b>「不可删」的口径</b>：7 个主干节点（{@link NodeCode}）在**任何**模板版本中都必须存在，
 * 且 {@code seq} 必须与主干序号一致。因此：
 * <ul>
 *   <li>对主干节点的 {@code DELETE /flow-nodes/{id}} → 直接 400 拒绝（本类
 *       {@link #assertDeletable(FlowNode)}）；</li>
 *   <li>设计器**新增**的非主干节点（抄送 / 额外审批等）可以删除；</li>
 *   <li>发布前 dry-run 再校验一次「7 个主干节点齐备且顺序正确」，防止绕过删除接口的脏数据。</li>
 * </ul>
 *
 * <p>依据 AC-48「在设计器中增删节点」：<b>增</b>节点不受限（除二期条件节点），
 * <b>删</b>只对非主干节点开放。这一取舍写进交付说明，避免「设计器能删主干」导致全集团流程断链。
 */
public final class RequiredNodePolicy {

    /** 主干 7 节点：{@code node_code → 固定 seq}（顺序即主干顺序）。 */
    private static final Map<String, Integer> TRUNK = trunk();

    private RequiredNodePolicy() {
    }

    private static Map<String, Integer> trunk() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (NodeCode code : NodeCode.values()) {
            map.put(code.code(), code.seq());
        }
        return Map.copyOf(map);
    }

    public static Map<String, Integer> trunkMap() {
        return TRUNK;
    }

    /** 是否为必填（主干）节点码。 */
    public static boolean isRequired(String nodeCode) {
        return nodeCode != null && TRUNK.containsKey(nodeCode.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** 删除前的硬校验：主干节点一律拒绝删除。 */
    public static void assertDeletable(FlowNode node) {
        if (node == null) {
            throw BizException.notFound("流程节点");
        }
        if (isRequired(node.getNodeCode())) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "主干必填节点不可删除：seq=" + node.getSeq() + " code=" + node.getNodeCode()
                            + "（doc/enums.md §2「主干节点固定 7 个、顺序固定」；如需调整请改决议模式/阈值或新增节点）")
                    .withDetail("nodeId", node.getId())
                    .withDetail("nodeCode", node.getNodeCode());
        }
    }

    /**
     * 模板级主干完整性校验（发布前 dry-run 的一项；**不抛异常**）。
     *
     * @return 问题清单（空 = 主干齐备且顺序正确）
     */
    public static List<String> violations(List<FlowNode> nodes) {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> seqOfCode = new LinkedHashMap<>();
        if (nodes != null) {
            for (FlowNode node : nodes) {
                if (node == null || node.getNodeCode() == null) {
                    continue;
                }
                seqOfCode.put(node.getNodeCode(), node.getSeq());
            }
        }
        for (Map.Entry<String, Integer> entry : TRUNK.entrySet()) {
            Integer actual = seqOfCode.get(entry.getKey());
            if (actual == null) {
                problems.add("缺少主干必填节点「" + entry.getKey() + "」（应为 seq=" + entry.getValue() + "）");
            } else if (!actual.equals(entry.getValue())) {
                problems.add("主干节点「" + entry.getKey() + "」的 seq 必须为 " + entry.getValue()
                        + "（主干顺序固定），实际 " + actual);
            }
        }
        for (Map.Entry<String, Integer> entry : seqOfCode.entrySet()) {
            if (FlowDefinitionEnums.NodeCode.of(entry.getKey()).isEmpty()) {
                // 非主干节点：允许存在（如抄送/额外审批），但必须排在主干之后且 seq 连续（由顺序校验统一判定）
                if (entry.getValue() != null && entry.getValue() <= NodeCode.values().length) {
                    problems.add("非主干节点「" + entry.getKey() + "」占用了主干序号 " + entry.getValue()
                            + "（主干 1–" + NodeCode.values().length + " 必须保留给 7 个主干节点）");
                }
            }
        }
        return problems;
    }
}
