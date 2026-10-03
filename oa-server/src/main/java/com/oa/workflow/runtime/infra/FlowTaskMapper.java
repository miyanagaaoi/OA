package com.oa.workflow.runtime.infra;

import com.oa.workflow.runtime.infra.row.FlowTaskRow;
import com.oa.workflow.runtime.infra.row.FlowTaskViewRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审批任务 Mapper（{@code flow_task}，doc/data-model.md §5.3）。
 *
 * <h2>数据域纪律（受控表）</h2>
 * <ul>
 *   <li>{@code flow_task} 登记为受控表（{@code oa.scope.tables}，{@code kind=INSTANCE}），
 *       因此本文件的**每条 SELECT 恰好 1 个** {@code @dataScope(table=flow_instance, alias=i)} 标记，
 *       且过滤主体一律是 {@code flow_instance}（别名 {@code i}）—— 任务查询全部 JOIN 它取单号/状态/发起人；
 *       标记若写成 {@code table=flow_task, alias=t}，片段会拼出 {@code t.initiator_id}（该表无此列）→ SQL 报错；</li>
 *   <li><b>待办列表就是「我是审批人」口径</b>：{@code t.assignee_id = :uid AND t.status = 'pending'}，
 *       叠加数据域后等于「域内 + 名下有未处理任务」；</li>
 *   <li>引擎内部（级联关闭、计数）由调用方包 {@code DataScopeContext.system()}，标记退化为 {@code 1=1}；</li>
 *   <li>不继承 {@code BaseMapper}；方法名避开 MP 通用读方法名（{@code selectById} 等）。</li>
 * </ul>
 */
@Mapper
public interface FlowTaskMapper {

    /** 按 id 取任务（数据域过滤：域外查不到 → 调用方按 404 处理）。 */
    FlowTaskRow selectTaskById(@Param("id") Long id);

    /** 节点实例下的全部任务（按 id 升序 = 产生顺序）。 */
    List<FlowTaskRow> selectByNodeInstance(@Param("nodeInstanceId") Long nodeInstanceId);

    /** 节点实例下的**主任务**（{@code add_sign_type IS NULL}，参与阈值计数）。 */
    List<FlowTaskRow> selectPrimaryByNodeInstance(@Param("nodeInstanceId") Long nodeInstanceId);

    /** 节点实例下**仍待处理**的主任务。 */
    List<FlowTaskRow> selectPendingPrimaryByNodeInstance(@Param("nodeInstanceId") Long nodeInstanceId);

    /**
     * 节点实例下**本轮**的主任务 —— 决议判定的唯一数据源。
     *
     * <p>轮次边界 = {@code flow_node_instance.started_at}（每次激活刷新为 {@code NOW()}）。
     * 必要性：节点可被「回退上一节点」重新激活（§7.2 第 10 行），上一轮的同意票仍在 {@code flow_task} 中
     * （任务只追加、不复用），不过滤会让重审**无需重新审批**即达到阈值（2026-10-03 运行期实测发现）。
     */
    List<FlowTaskRow> selectRoundPrimaryByNodeInstance(@Param("nodeInstanceId") Long nodeInstanceId);

    /** 实例下的全部任务（详情出参）。 */
    List<FlowTaskViewRow> selectTasksByInstance(@Param("instanceId") Long instanceId);

    /** 某人在某单上的待处理主任务（依次签的下一位定位）。 */
    FlowTaskRow selectPendingPrimaryByInstanceAndAssignee(@Param("instanceId") Long instanceId,
                                                          @Param("assigneeId") Long assigneeId);

    /** 某单尚未决议的待办数（实例终态时的联动统计）。 */
    int countPendingByInstance(@Param("instanceId") Long instanceId);

    // ------------------------------------------------------------------ 列表（分页 + 数据域）

    /** 待办（{@code status='pending'} 且 assignee=我）。 */
    List<FlowTaskViewRow> selectTodo(@Param("userId") Long userId,
                                     @Param("offset") Integer offset,
                                     @Param("size") Integer size);

    /** 待办总数。 */
    long countTodo(@Param("userId") Long userId);

    /** 已办（我处理过的任务：非 pending 的全部终态）。 */
    List<FlowTaskViewRow> selectDone(@Param("userId") Long userId,
                                     @Param("offset") Integer offset,
                                     @Param("size") Integer size);

    /** 已办总数。 */
    long countDone(@Param("userId") Long userId);

    /** 我发起的（以 {@code flow_instance.initiator_id} 为准）。 */
    List<FlowTaskViewRow> selectInitiated(@Param("userId") Long userId,
                                          @Param("offset") Integer offset,
                                          @Param("size") Integer size);

    /** 我发起的总数。 */
    long countInitiated(@Param("userId") Long userId);

    // ------------------------------------------------------------------ 写（无需标记）

    /** 新增任务（自增主键回填）。 */
    int insert(FlowTaskRow row);

    /** 决议落库：{@code status / opinion / decided_at=NOW()}（只允许从 pending 迁移）。 */
    int updateDecision(@Param("id") Long id, @Param("status") String status, @Param("opinion") String opinion);

    /** 同节点其余待处理任务 → {@code auto_closed}（7.2 联动：或签/会签达标、节点驳回）。 */
    int closePendingByNodeInstance(@Param("nodeInstanceId") Long nodeInstanceId);

    /** 全实例待处理任务 → {@code auto_closed}（实例终态联动）。 */
    int closePendingByInstance(@Param("instanceId") Long instanceId);

    /** 转办/改派：原任务置终态（{@code transferred} / {@code reassigned}），受让人另起新任务。 */
    int updateHandover(@Param("id") Long id, @Param("status") String status,
                       @Param("handoverReason") String handoverReason);
}
