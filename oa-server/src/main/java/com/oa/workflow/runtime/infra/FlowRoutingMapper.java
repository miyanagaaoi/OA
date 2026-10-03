package com.oa.workflow.runtime.infra;

import com.oa.workflow.runtime.infra.row.FlowRoutingRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流转链 Mapper（{@code flow_routing}，doc/data-model.md §5.4）—— <b>受控表</b>。
 *
 * <h2>数据域纪律</h2>
 * <p>{@code flow_routing} 登记为受控表（{@code oa.scope.tables}，{@code kind=INSTANCE}），
 * 因此本文件每条 SELECT **恰好 1 个** {@code /* @dataScope(table=flow_instance, alias=i) *}{@code /} 标记，
 * 且一律 JOIN {@code flow_instance i} —— 过滤主体恒为实例（标记写成 {@code table=flow_routing, alias=r}
 * 会拼出 {@code r.initiator_id}，该表无此列 → SQL 报错）。
 *
 * <p>与 {@link FlowRuntimeMapper}（补件 / 轨迹 / 抄送，**非受控表**）分开的两个理由：
 * ① 受控表标记的纪律是**按文件**校验的（{@code WorkflowMapperXmlTest#markerDiscipline}），
 * 混在一个文件里会逼着给非受控表的 SELECT 也加标记（错标记 → 运行期 SQL 报错）；
 * ② 「谁过滤主体」这件事在一个文件里只有一种答案，评审时不必逐条分辨。
 */
@Mapper
public interface FlowRoutingMapper {

    /** 实例下的流转链（按 seq 升序）。 */
    List<FlowRoutingRow> selectRoutingByInstance(@Param("instanceId") Long instanceId);

    /** 实例最后一条流转记录。 */
    FlowRoutingRow selectLastRouting(@Param("instanceId") Long instanceId);

    /** 处理中的回退记录（**上一节点重审中**；该行置 finished 即「重审完成」）。 */
    FlowRoutingRow selectProcessingRollback(@Param("instanceId") Long instanceId,
                                            @Param("toNodeSeq") Integer toNodeSeq);

    /** 是否流转到过某部门（**禁止回流到已处理部门**，REQ-FLOW-020）。 */
    int countRoutingToDept(@Param("instanceId") Long instanceId, @Param("toDeptId") Long toDeptId);

    /**
     * 最近若干条流转记录（按 {@code seq} 倒序）。
     *
     * <p>用途：判定「同一部门连续『回到本部门』≤2」（REQ-FLOW-022）与收束 {@code processing} 的流转记录
     * —— 由调用方逐条回溯，而不是在 SQL 里写递归/变量（口径集中在 {@code FlowEngineService}）。
     */
    List<FlowRoutingRow> selectRecentRoutings(@Param("instanceId") Long instanceId,
                                              @Param("limit") Integer limit);

    // ------------------------------------------------------------------ 写（无需标记）

    int insertRouting(FlowRoutingRow row);

    int finishRouting(@Param("id") Long id);
}
