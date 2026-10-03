package com.oa.identity.infra;

import com.oa.identity.infra.row.InFlightItemRow;
import com.oa.identity.infra.row.PendingTaskRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 在途单据 / 待办的真实查询（{@code flow_instance} / {@code flow_task} / {@code sys_org}）。
 *
 * <p>本接口是 {@code DefaultInFlightChecker} 的数据访问层，服务于
 * 「组织停用前必须清空在途」「员工离职/停用前必须清空待办」两类补偿控制
 * （PRD §5.5、AC-11、AC-12、import-spec §8.1/§8.2）。
 *
 * <h2>数据域纪律</h2>
 * <ul>
 *   <li>{@code flow_instance} / {@code flow_task} 都是**受控表**，因此每条 SELECT 带且只带
 *       1 个 {@code @dataScope} 标记；标记指向 {@code flow_instance}（别名 {@code i}）——
 *       它是所有查询的过滤主体（任务查询也 JOIN 它取单号与节点名）；</li>
 *   <li>调用方 {@code DefaultInFlightChecker} **一律使用 {@code DataScopeContext.system()}**：
 *       这是「离职/停用前的影响面检查」，语义上是**系统口径**的统计
 *       （不能因为调用人的数据域而漏算在途单据，否则补偿控制失效）；
 *       该用法在 {@code InFlightChecker} 的接口注释里已定稿；</li>
 *   <li>不继承 {@code BaseMapper}（MP 注入语句无标记 → 40303）。</li>
 * </ul>
 */
@Mapper
public interface InFlightQueryMapper {

    /** 组织子树下的在途单据数（{@code initiator_org_path} 前缀 ∪ 当前承接部门在子树内）。 */
    int countOrgInFlight(@Param("orgPathPrefix") String orgPathPrefix);

    /** 组织子树下的在途单据明细（按创建时间倒序）。 */
    List<InFlightItemRow> selectOrgInFlightItems(@Param("orgPathPrefix") String orgPathPrefix,
                                                 @Param("limit") Integer limit);

    /** 该组织子树下的在途单号（用于拦截文案里的「涉及单号」）。 */
    List<String> selectOrgInFlightBizNos(@Param("orgPathPrefix") String orgPathPrefix,
                                         @Param("limit") Integer limit);

    /** 该人名下的待处理待办数（{@code flow_task.status='pending'}）。 */
    int countUserPendingTasks(@Param("userId") Long userId);

    /** 批量待办数（列表页避免逐行 N+1）：一次 GROUP BY 取回，未命中的由调用方补 0。 */
    List<PendingTaskRow> selectPendingTaskCounts(@Param("userIds") Collection<Long> userIds);

    /** 该人名下的待办明细（工作交接清单）。 */
    List<PendingTaskRow> selectUserPendingTasks(@Param("userId") Long userId, @Param("limit") Integer limit);

    /** 该人名下待办所涉单号（拦截文案用）。 */
    List<String> selectUserPendingBizNos(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 该人身处**活动节点候选**内的在途单据数（2a.4 追加口径，见 {@code TODO(2a.4)} 的消费）。
     *
     * <p>口径：{@code flow_node_instance.status='active'} 且 {@code approver_ids_json} 含本人
     * （{@code JSON_CONTAINS}）。用途：**依次审批**的后续候选人、以及或签/会签尚未产生任务前的
     * 「已固化在快照里的候选人」——他们此刻名下没有待办，但离职会让在途单据卡死
     * （PRD §5.5「快照会导致单据永久卡死」正是这条补偿控制的理由）。
     *
     * <p>{@code flow_node_instance} **不是**受控表，但本语句 JOIN 了 {@code flow_instance}，
     * 因此仍带 1 个 {@code @dataScope} 标记，并由调用方在 {@code DataScopeContext.system()} 下执行。
     */
    int countUserCandidateNodes(@Param("userId") Long userId);

    /** 活动节点候选单据的单号（拦截文案用）。 */
    List<String> selectUserCandidateBizNos(@Param("userId") Long userId, @Param("limit") Integer limit);

    /** 该人名下的在途/待办明细（人员影响清单：单据类型 / 发起人 / 当前节点 / 状态）。 */
    List<InFlightItemRow> selectUserInFlightItems(@Param("userId") Long userId, @Param("limit") Integer limit);

    /** 活动节点候选单据明细（人员影响清单的第二段：候选但尚无待办）。 */
    List<InFlightItemRow> selectUserCandidateInFlightItems(@Param("userId") Long userId,
                                                          @Param("limit") Integer limit);
}
