package com.oa.workflow.runtime.infra;

import com.oa.workflow.runtime.infra.row.FlowCcRow;
import com.oa.workflow.runtime.infra.row.FlowSupplementRow;
import com.oa.workflow.runtime.infra.row.FlowThreadRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 运行时从表 Mapper：补件（{@code flow_supplement}）、审批轨迹（{@code sys_thread}）、抄送（{@code flow_cc}）。
 *
 * <h2>数据域纪律</h2>
 * <ul>
 *   <li>这三张表**都不是**受控表（未登记在 {@code oa.scope.tables}），因此本文件的 SELECT
 *       **刻意 0 个 {@code @dataScope} 标记** —— 加了标记会被当成 {@code flow_instance} 去拼片段
 *       （没有对应列 → SQL 报错）；</li>
 *   <li>可见性依附于**前置校验**：所有对外读取必须先经 {@code FlowInstanceMapper#selectInstanceById}
 *       （带标记、域外 404）拿到实例行，再按 {@code instance_id} 读本表
 *       （见 {@code FlowRuntimeQueryService} 的注释）；</li>
 *   <li>流转链（{@code flow_routing}，**是**受控表）另立文件：{@link FlowRoutingMapper}；</li>
 *   <li>不继承 {@code BaseMapper}。</li>
 * </ul>
 */
@Mapper
public interface FlowRuntimeMapper {

    // ================================================================ 补件

    /** 实例下处理中的补件请求（同一时刻最多一条）。 */
    FlowSupplementRow selectPendingSupplement(@Param("instanceId") Long instanceId);

    /** 实例下的全部补件记录（按轮次）。 */
    List<FlowSupplementRow> selectSupplementsByInstance(@Param("instanceId") Long instanceId);

    int insertSupplement(FlowSupplementRow row);

    int markSupplementSubmitted(@Param("id") Long id, @Param("submittedBy") Long submittedBy,
                                @Param("submittedNote") String submittedNote);

    int markSupplementOverdue(@Param("id") Long id);

    /** 实例被撤回/终止时，未完成的补件请求置 {@code cancelled}（DDL 注释口径）。 */
    int cancelPendingSupplements(@Param("instanceId") Long instanceId);

    // ================================================================ 审批轨迹（只追加）

    /** 实例下的轨迹（按 seq）。 */
    List<FlowThreadRow> selectThreadByInstance(@Param("instanceId") Long instanceId);

    /** 下一个轨迹序号（{@code MAX(seq)+1}；同一实例的并发动作由实例行锁串行化）。 */
    Integer nextThreadSeq(@Param("instanceId") Long instanceId);

    int insertThread(FlowThreadRow row);

    // ================================================================ 抄送

    List<FlowCcRow> selectCcByInstance(@Param("instanceId") Long instanceId);

    /** 新增抄送（唯一键 {@code (instance_id, user_id)} → 重复抄送幂等）。 */
    int insertCc(FlowCcRow row);

    int markCcRead(@Param("instanceId") Long instanceId, @Param("userId") Long userId);
}
