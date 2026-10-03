package com.oa.workflow.approver.infra;

import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.approver.infra.row.FormDataRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程实例 Mapper（{@code flow_instance} / {@code form_data}，doc/data-model.md §5.1）。
 *
 * <h2>数据域纪律</h2>
 * <ul>
 *   <li>{@code flow_instance} / {@code form_data} 都是**受控表**（{@code oa.scope.tables}），
 *       因此本文件的每条 SELECT 都带且只带 1 个 {@code @dataScope} 标记；</li>
 *   <li><b>读实例（API 出参）不使用系统口径</b>：{@code selectInstanceById} / {@code selectList} 在**调用人的数据域**下执行，
 *       域外实例直接查不到（按 404 处理，与既有 {@code OrgService#requireVisibleOrg} 同口径）；</li>
 *   <li><b>引擎内部</b>（解析、固化快照、建实例、提交）由 {@code FlowInstanceService} 包裹
 *       {@code DataScopeContext.system()} —— 那时查的是「刚才自己写进去的那一行」，
 *       与授权无关（同 {@code JdbcApproverDirectory} 的说明）；</li>
 *   <li>不继承 {@code BaseMapper}（MP 注入语句无标记，会被 40303 拒绝）。</li>
 * </ul>
 */
@Mapper
public interface FlowInstanceMapper {

    /** 按 id 取实例（数据域过滤：域外返回 {@code null} → 调用方按 404 处理）。 */
    FlowInstanceRow selectInstanceById(@Param("id") Long id);

    /** 实例列表（可按模板 / 状态 / 发起人过滤；数据域过滤）。 */
    List<FlowInstanceRow> selectInstances(@Param("templateId") Long templateId,
                                     @Param("templateVersion") Integer templateVersion,
                                     @Param("status") String status,
                                     @Param("initiatorId") Long initiatorId,
                                     @Param("limit") Integer limit);

    /** 按单号取（唯一键口径；数据域过滤）。 */
    FlowInstanceRow selectByBizNo(@Param("bizNo") String bizNo);

    /**
     * 单号是否已存在（**唯一性是全局约束**，与调用人数据域无关）。
     *
     * <p>与 {@code SysUserMapper.countByAccountSystem} 的做法不同：本语句**保留**
     * {@code @dataScope} 标记，由调用方（{@code FlowInstanceService}）在
     * {@code DataScopeContext.system()} 下执行 —— 这样既拿到全库口径，
     * 又**不需要**往 {@code oa.scope.exempt-statement-ids} 里加窄豁免条目
     * （豁免面越小越好；见 {@code DataScopeMapperGuardTest}）。
     */
    int countByBizNo(@Param("bizNo") String bizNo);

    /** 该模板（可指定版本）下**在途**（{@code approving}）的实例数。 */
    int countInFlightByTemplate(@Param("templateId") Long templateId,
                                @Param("templateVersion") Integer templateVersion);

    // ------------------------------------------------------------------ 写

    int insertInstance(FlowInstanceRow instance);

    /** 更新快照（重解析；旧快照由调用方写入审计日志）。 */
    int updateSnapshot(@Param("id") Long id, @Param("approverSnapshotJson") String approverSnapshotJson);

    /** 草稿 → 审批中（提交）：写 submitted_at 与当前节点序号。 */
    int markSubmitted(@Param("id") Long id, @Param("currentNodeSeq") Integer currentNodeSeq);

    /** 回填当前节点序号（2a.4 运行时使用；本工作包只写首次提交值）。 */
    int updateCurrentNodeSeq(@Param("id") Long id, @Param("currentNodeSeq") Integer currentNodeSeq);

    // ------------------------------------------------------------------ 运行时（2a.4）

    /**
     * 实例进度落库（状态 / 子状态 / 当前节点 / 当前承接部门）。
     *
     * <p>{@code subStatus} 传 {@code null} 即**清空**子状态（待补件提交后的口径，§7.2）。
     */
    int updateProgress(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("subStatus") String subStatus,
                       @Param("currentNodeSeq") Integer currentNodeSeq,
                       @Param("currentDeptId") Long currentDeptId);

    /** 进入终态：写 {@code status} 与 {@code finished_at}，并清空 {@code sub_status}。 */
    int markFinished(@Param("id") Long id, @Param("status") String status);

    /** {@code routing_count} 与 {@code routing_seq} 同时 +1（Q6 的「流转 + 回退」计数口径）。 */
    int incrementRoutingCount(@Param("id") Long id);

    /** {@code supplement_count} +1（Q6 的补件计数口径；**请求补件时**占位，见 data-model §5.5）。 */
    int incrementSupplementCount(@Param("id") Long id);

    /**
     * 驳回后重提的实例复位：回到 {@code draft}、清子状态/当前节点/承接部门/完成时间，
     * 并换上**重新解析**后的模板版本与快照（REQ-FLOW-017）。
     *
     * <p>刻意**不重置** {@code routing_count} / {@code supplement_count}：Q6 两列是「<b>全单</b>累计」
     * 计数（templates.md §1.7），重提不清零（如需清零须另立产品裁定）。
     */
    int resetForResubmit(@Param("id") Long id,
                         @Param("templateId") Long templateId,
                         @Param("templateVersion") Integer templateVersion,
                         @Param("approverSnapshotJson") String approverSnapshotJson);

    // ------------------------------------------------------------------ form_data

    /** 新增表单数据行（biz_no 唯一），自增主键回填到 {@code row.id}。 */
    int insertFormData(FormDataRow row);

    /** 按 id 取表单数据的归属人（数据域过滤）。 */
    Long selectFormDataCreator(@Param("id") Long id);

    /**
     * 按 id 取表单字段值 JSON（重解析快照时重建「表单事实」，用于跳过条件求值）。
     *
     * <p>数据域过滤：只能读到调用人可见单据的字段值。
     */
    String selectFormDataFields(@Param("id") Long id);
}
