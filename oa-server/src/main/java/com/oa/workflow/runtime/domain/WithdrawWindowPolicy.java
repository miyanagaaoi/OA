package com.oa.workflow.runtime.domain;

import com.oa.workflow.definition.domain.FlowGateEnums.WithdrawWindow;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import java.util.List;

/**
 * <b>撤回窗口纯判定</b>（2026-10-04 产品裁定：撤回窗口是**模板级配置项**）。
 *
 * <p>真源：{@code doc/templates.md} §1.8「撤回窗口配置项 `withdraw_window`」；
 * 背景是 {@code doc/prd-0.1.md} 里 REQ-FLOW-009（撤回仅限节点②<b>通过</b>之前 ⇒ ②审批中可撤）
 * 与 AC-16（②审批中 → 撤回失败）的**真实矛盾** —— 裁定为「不再二选一，按模板可配」，
 * 默认取 REQ-FLOW-009 口径（历史行为不变）。
 *
 * <h2>判据（只依赖节点实例的 {@code nodeSeq} 与 {@code status}）</h2>
 * <p>刻意**不依赖** {@code flow_instance.current_node_seq}：流转/回退会把当前序号改到别的部门节点上，
 * 而撤回窗口是**沿②这条主干**判定的。
 *
 * <pre>
 * 节点实例形状                                        until_finance_approved（默认）  until_finance_started（严格）
 * seq==2 且 approved                                  拒                             拒
 * seq&gt;2 且已离开 pending（非 cancelled）              拒                             拒
 * seq==2 且 active / waiting_supplement / returned    放行                           拒
 * seq==2 且 pending                                   放行                           放行
 * 其余（含 seq&gt;2 的 pending / cancelled）            放行                           放行
 * </pre>
 *
 * <h2>为什么严格口径只加一条</h2>
 * <p>{@link #UNTIL_FINANCE_STARTED_SET} 恰是「节点②<b>已开始处理</b>」的全部在途态：
 * {@code active}（已受理）/ {@code waiting_supplement}（已发出补件请求）/ {@code returned}（已被回退待重审）。
 * {@code pending} = 尚未轮到本节点（未开始，仍可撤）、{@code cancelled} = 终态级联的产物（不算越界）、
 * {@code approved} 由第一条兜住、{@code skipped}/{@code rejected} 是②的终态（已有其它守卫）。
 *
 * <p><b>只增不减</b>：严格口径是在默认口径之上**再加一条拒绝**，因此「②通过后一律拒」「②之后被推进即拒」
 * 两条既有语义在两种口径下都保持 —— 这也是本类被写成「先判公共拒绝、再判口径增量」的原因。
 */
public final class WithdrawWindowPolicy {

    /** 节点②（财务部复核）的固定序号（doc/enums.md §2）。 */
    public static final int FINANCE_SEQ = 2;

    /** 严格口径下「②已开始处理」的节点实例状态集合（见类注释）。 */
    private static final List<NodeStatus> UNTIL_FINANCE_STARTED_SET =
            List.of(NodeStatus.ACTIVE, NodeStatus.WAITING_SUPPLEMENT, NodeStatus.RETURNED);

    private WithdrawWindowPolicy() {
    }

    /**
     * 归一「配置 → 生效口径」：{@code null}（列 {@code NULL} / 读不到模板行）→ 默认口径。
     *
     * <p><b>唯一入口</b>：调用方（引擎 / 接口 / 日志文案）都从这里取生效值，
     * 不要各自判 {@code null} —— 否则「某个地方把 null 当默认、另一个地方当不限」的口径分叉会重演。
     */
    public static WithdrawWindow effective(WithdrawWindow window) {
        return window == null ? WithdrawWindow.defaultWindow() : window;
    }

    /**
     * 是否允许撤回。<b>出参口径由调用方从「实例锁定的模板版本」取</b>
     * （见 {@code com.oa.workflow.runtime.app.FlowGateService#withdrawWindowOf}）。
     *
     * @param window 生效口径；{@code null} 按 {@link WithdrawWindow#defaultWindow()}（默认口径）处理
     * @param nodes  该实例的全部节点实例；{@code null} 视为空集合（无证据 → 放行，与既有实现一致）
     */
    public static boolean allowed(WithdrawWindow window, List<? extends NodeView> nodes) {
        WithdrawWindow effective = effective(window);
        if (nodes == null) {
            return true;
        }
        for (NodeView node : nodes) {
            if (node == null || node.nodeSeq() == null) {
                continue;
            }
            // ① 公共拒绝：②已通过 ⇒ 已越过撤回窗口（REQ-FLOW-009 的正面口径，两种口径一致）
            if (node.nodeSeq() == FINANCE_SEQ && NodeStatus.APPROVED.code().equals(node.status())) {
                return false;
            }
            // ② 公共拒绝：②之后的节点「被推进过」即视为已越过②；
            //    未到达的 pending 与终态级联的 cancelled 都不算（pending 语义见 doc/enums.md §5）
            if (node.nodeSeq() > FINANCE_SEQ && advancedBeyondPending(node.status())) {
                return false;
            }
            // ③ 严格口径增量：②已开始处理（active / waiting_supplement / returned）即不可撤（AC-16 口径）
            if (effective == WithdrawWindow.UNTIL_FINANCE_STARTED
                    && node.nodeSeq() == FINANCE_SEQ && startedAtFinance(node.status())) {
                return false;
            }
        }
        return true;
    }

    /** 节点实例是否「已离开未开始态」（{@code pending} = 未开始，不构成「已越过②」的证据）。 */
    public static boolean advancedBeyondPending(String status) {
        if (status == null) {
            return false;
        }
        NodeStatus parsed = NodeStatus.of(status).orElse(null);
        return parsed != null && parsed != NodeStatus.PENDING && parsed != NodeStatus.CANCELLED;
    }

    /** 节点②是否「已开始处理」（严格口径的唯一增量判据）。 */
    public static boolean startedAtFinance(String status) {
        if (status == null) {
            return false;
        }
        NodeStatus parsed = NodeStatus.of(status).orElse(null);
        return parsed != null && UNTIL_FINANCE_STARTED_SET.contains(parsed);
    }

    /**
     * 判定的**最小视图**：把运行时行（{@code FlowNodeInstanceRow}）与测试夹具统一到两个字段上，
     * 使本策略不依赖 MyBatis 行对象（纯函数、可单测）。
     */
    public interface NodeView {

        /** 节点序号（可空 = 脏数据，跳过）。 */
        Integer nodeSeq();

        /** 节点实例状态码（{@code pending} / {@code active} / …，见 doc/enums.md §5）。 */
        String status();
    }
}
