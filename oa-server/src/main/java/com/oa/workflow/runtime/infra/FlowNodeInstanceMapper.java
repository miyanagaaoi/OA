package com.oa.workflow.runtime.infra;

import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 节点实例 Mapper（{@code flow_node_instance}，doc/data-model.md §5.2）。
 *
 * <h2>数据域纪律</h2>
 * <ul>
 *   <li>{@code flow_node_instance} <b>不是</b>受控表（未登记在 {@code oa.scope.tables}，
 *       其可见性完全依附于 {@code flow_instance}），因此本文件的 SELECT **刻意不带**
 *       {@code @dataScope} 标记 —— 加标记会把它当成 {@code flow_instance} 去拼片段（没有对应列，
 *       直接 SQL 报错）；</li>
 *   <li><b>可见性由调用方先判</b>：所有对外读取必须先经 {@code FlowInstanceMapper#selectInstanceById}
 *       （带数据域标记，域外 404）拿到实例行，再按 {@code instance_id} 读本表；</li>
 *   <li>不继承 {@code BaseMapper}（MP 注入语句绕过显式语句清单）；方法名刻意避开 MP 的通用读方法名
 *       （{@code selectById} 等），由 {@code WorkflowMapperXmlTest} 正向断言。</li>
 * </ul>
 */
@Mapper
public interface FlowNodeInstanceMapper {

    /** 实例下的全部节点实例（按 {@code node_seq, dept_id, id} 排序 —— 主干序即展示序）。 */
    List<FlowNodeInstanceRow> selectByInstance(@Param("instanceId") Long instanceId);

    /** 按 id 取节点实例。 */
    FlowNodeInstanceRow selectNodeInstanceById(@Param("id") Long id);

    /** 按唯一键口径取（{@code node_key = node_seq:node_code:dept_id|0}）。 */
    FlowNodeInstanceRow selectByInstanceAndKey(@Param("instanceId") Long instanceId,
                                               @Param("nodeKey") String nodeKey);

    /** 实例下**未完成**（{@code pending/active/waiting_supplement/returned}）的节点实例。 */
    List<FlowNodeInstanceRow> selectLiveByInstance(@Param("instanceId") Long instanceId);

    /** 实例下某序号的节点实例（协同组/流转承接会有多条）。 */
    List<FlowNodeInstanceRow> selectByInstanceAndSeq(@Param("instanceId") Long instanceId,
                                                     @Param("seq") Integer seq);

    /** 上一个已通过的节点实例（回退上一节点的目标；{@code node_seq < seq} 且状态已通过）。 */
    FlowNodeInstanceRow selectPreviousApproved(@Param("instanceId") Long instanceId,
                                               @Param("seq") Integer seq);

    /** 处于「已退回」状态的节点实例（回退后等待上一节点重审的**发起方**）。 */
    FlowNodeInstanceRow selectReturnedNode(@Param("instanceId") Long instanceId);

    /** 某序号下仍**未完成**的节点实例数（协同组/流转：为 0 才能推进主干）。 */
    int countLiveAtSeq(@Param("instanceId") Long instanceId, @Param("seq") Integer seq);

    /** 某序号下已通过/已跳过的节点实例数（用于出参与校验）。 */
    int countFinishedAtSeq(@Param("instanceId") Long instanceId, @Param("seq") Integer seq);

    /** 该实例是否已有节点实例（首次提交 vs 重提的分叉判定）。 */
    int countByInstance(@Param("instanceId") Long instanceId);

    // ------------------------------------------------------------------ 写

    /** 新增节点实例（自增主键回填 {@code row.id}）。 */
    int insert(FlowNodeInstanceRow row);

    /** 仅改状态。 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 激活：{@code status='active'}、{@code started_at=IFNULL(started_at, NOW())}、清 {@code finished_at}。 */
    int activate(@Param("id") Long id);

    /** 结束：{@code status=#{status}}、{@code finished_at=NOW()}。 */
    int finish(@Param("id") Long id, @Param("status") String status);

    /** 覆盖本节点实例的运行时候选人（流转承接 / 协同组 / 补件回位）。 */
    int updateApprovers(@Param("id") Long id, @Param("approverIdsJson") String approverIdsJson);

    /** 写加签链（{@code add_sign_chain_json}；**不触碰快照**）。 */
    int updateAddSignChain(@Param("id") Long id, @Param("addSignChainJson") String addSignChainJson);

    /** 被回退次数 +1（上限 2，REQ-FLOW-021）。 */
    int incrementReturnedCount(@Param("id") Long id);

    /** 请求补件：{@code status='waiting_supplement'} 且 {@code supplement_requested=1}（同节点上限 1 次）。 */
    int markSupplementWaiting(@Param("id") Long id);

    /** 补件提交后回到进行中（{@code supplement_requested} **保留 1**：同节点上限已消耗）。 */
    int markSupplementResolved(@Param("id") Long id);

    /** 重提前复位（新一轮从①重走：状态回 pending、清计数与时间戳、清加签链）。 */
    int resetForNewRound(@Param("id") Long id);
}
