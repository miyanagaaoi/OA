package com.oa.workflow.task.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.ActionResult;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.CcListItemView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.PageResult;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.TaskListItemView;
import com.oa.workflow.runtime.app.FlowEngineService;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import com.oa.workflow.runtime.infra.row.FlowCcViewRow;
import com.oa.workflow.runtime.infra.row.FlowTaskViewRow;
import com.oa.workflow.task.domain.TaskListFilter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务与决议服务（2a.5）：待办 / 已办 / 我发起的（分页 + 数据域），以及全部任务动作的入口。
 *
 * <h2>职责边界</h2>
 * <ul>
 *   <li><b>列表</b>：本类承担。待办口径 = {@code flow_task.status='pending' AND assignee=我}
 *       （PRD §6.7 / 门户工作台「待我审批」），叠加数据域织入（域外单据不出现，不是「显示但点不开」）；</li>
 *   <li><b>动作</b>：一律**委托**给 {@link FlowEngineService}（引擎层统一实现 §7.2 联动），
 *       本类不写任何状态迁移；</li>
 *   <li>分页上限 100 条/页，{@code page} 从 1 起（防「一次拉全表」）。</li>
 * </ul>
 *
 * <h2>三个列表的口径（互相不重复）</h2>
 * <table border="1">
 *   <tr><th>列表</th><th>口径</th><th>SQL 依据</th></tr>
 *   <tr><td>待办</td><td>{@code flow_task.status='pending'} 且 {@code assignee_id=我}</td>
 *       <td>{@code FlowTaskMapper#selectTodo}</td></tr>
 *   <tr><td>已办</td><td>{@code assignee_id=我} 且 {@code status<>'pending'}（含已自动关闭）</td>
 *       <td>{@code FlowTaskMapper#selectDone}</td></tr>
 *   <tr><td>我发起的</td><td>{@code flow_instance.initiator_id=我}（不看任务表）</td>
 *       <td>{@code FlowTaskMapper#selectInitiated}</td></tr>
 * </table>
 */
@Service
public class FlowTaskService {

    /** 单页上限（防拉全表）。 */
    public static final int MAX_PAGE_SIZE = 100;

    private final FlowTaskMapper taskMapper;
    private final FlowEngineService engine;
    private final WorkflowPermissionService permissionService;

    public FlowTaskService(FlowTaskMapper taskMapper,
                           FlowEngineService engine,
                           WorkflowPermissionService permissionService) {
        this.taskMapper = taskMapper;
        this.engine = engine;
        this.permissionService = permissionService;
    }

    // ================================================================ 列表

    /** 待办（真实口径：pending 且我是处理人；筛选见 {@link TaskListFilter}）。 */
    public PageResult<TaskListItemView> todo(Integer page, Integer size, TaskListFilter filter) {
        CurrentUser actor = permissionService.requireInitiator("查看我的待办");
        int p = normalizePage(page);
        int s = normalizeSize(size);
        TaskListFilter f = filter == null ? TaskListFilter.none() : filter;
        long total = taskMapper.countTodo(actor.id(), f);
        List<TaskListItemView> items = toItems(taskMapper.selectTodo(actor.id(), f, offset(p, s), s));
        return new PageResult<>(items, total, p, s, PageResult.DATE_FIELD_CREATED_AT);
    }

    /**
     * 已办（我处理过的任务）。
     *
     * <p><b>日期口径（2026-10-05 裁定）</b>：本列表的 {@code dateFrom/dateTo} 按
     * <b>我处理该任务的时间</b>（{@code flow_task.decided_at}）筛，<b>不按发起时间</b> ——
     * 用户在这个列表里找的是「我哪天办的那张单」，发起时间会在单据流转多日后才落到我手上时
     * 给出误导性结果。其余三个列表维持「按发起时间」不变（见 {@code TaskListFilter} 的类注释）。
     * <p>出参 {@code dateField=decidedAt} 明示这一口径，条目上的 {@code decidedAt} 即筛选所依据的值。
     */
    public PageResult<TaskListItemView> done(Integer page, Integer size, TaskListFilter filter) {
        CurrentUser actor = permissionService.requireInitiator("查看我已办");
        int p = normalizePage(page);
        int s = normalizeSize(size);
        TaskListFilter f = filter == null ? TaskListFilter.none() : filter;
        long total = taskMapper.countDone(actor.id(), f);
        List<TaskListItemView> items = toItems(taskMapper.selectDone(actor.id(), f, offset(p, s), s));
        return new PageResult<>(items, total, p, s, PageResult.DATE_FIELD_DECIDED_AT);
    }

    /** 我发起的（以实例发起人为准；日期口径 = 发起时间）。 */
    public PageResult<TaskListItemView> initiated(Integer page, Integer size, TaskListFilter filter) {
        CurrentUser actor = permissionService.requireInitiator("查看我发起的");
        int p = normalizePage(page);
        int s = normalizeSize(size);
        TaskListFilter f = filter == null ? TaskListFilter.none() : filter;
        long total = taskMapper.countInitiated(actor.id(), f);
        List<TaskListItemView> items = toItems(taskMapper.selectInitiated(actor.id(), f, offset(p, s), s));
        return new PageResult<>(items, total, p, s, PageResult.DATE_FIELD_CREATED_AT);
    }

    /**
     * <b>抄送我的一览</b>（{@code GET /flow-tasks/cc}）。
     *
     * <h2>数据域口径（先取证再落地）</h2>
     * <p>{@code flow_cc} **不在**受控表清单里（{@code oa.scope.tables} 只有
     * {@code flow_instance / form_data / flow_task / flow_routing / sys_user}，
     * 见 {@code DataScopeTableRegistry}），因此它没有可织入的数据域语义。
     * 本列表的口径由**两段**组成：
     * <ol>
     *   <li><b>抄送人 = 本人</b>：{@code flow_cc.user_id = 当前登录人}（表唯一键
     *       {@code uk_flow_cc (instance_id, user_id)} 保证「一单一抄送人一行」，不会重复）；</li>
     *   <li><b>单据在我的数据域内</b>：语句带 {@code @dataScope(table=flow_instance, alias=i)}，
     *       由 {@code DataScopeInterceptor} 织入 —— 域外单据**不出现**（不是「显示但点不开」）。</li>
     * </ol>
     * <p><b>为什么不是「抄送即可见」</b>：{@code DataScopeSqlBuilder} 的 self 口径已经把
     * 「我是抄送人」并入可见性（{@code EXISTS (flow_cc ...)}），所以第 2 段对 self 类角色
     * 不产生额外裁剪；对 {@code company} 类角色，抄送域外单据同样不可见 —— 与
     * AC-02 / TC-AUTH-024「6 个入口均无泄露」一致（宁可少显示，不可泄露）。
     *
     * <p>抄送**只读可见、不产生待办**（PRD REQ-MSG-003 / AC-54）：本列表不出任务维度字段。
     *
     * <p><b>日期口径（2026-10-05 裁定，与「其余列表按发起时间」一致）</b>：本列表的
     * {@code dateFrom/dateTo} **仍按单据发起时间**（{@code i.created_at}）筛，出参
     * {@code dateField=createdAt}；对应条目上的 {@code instanceCreatedAt}。
     * 「抄送时间」（{@code c.created_at}）是另一条合理口径（该列表本来就是按抄送时间倒序的），
     * 但同一套参数在四个端点里保持「同名同义」更不易误用，且抄送时间在条目上已可单独读取，
     * 因此本轮不做切换 —— 若产品侧要求按抄送时间筛，只需把它与 {@code done} 一起切到
     * 各自的列并同步 {@code dateField}（见交付说明待决策）。
     */
    public PageResult<CcListItemView> cc(Integer page, Integer size, TaskListFilter filter) {
        CurrentUser actor = permissionService.requireInitiator("查看抄送我的");
        int p = normalizePage(page);
        int s = normalizeSize(size);
        TaskListFilter f = filter == null ? TaskListFilter.none() : filter;
        long total = taskMapper.countCcOverview(actor.id(), f);
        List<CcListItemView> items = new ArrayList<>();
        for (FlowCcViewRow row : taskMapper.selectCcOverview(actor.id(), f, offset(p, s), s)) {
            items.add(new CcListItemView(row.getCcId(), row.getInstanceId(), row.getBizNo(), row.getFormType(),
                    row.getCategory(), row.getTitle(), row.getInitiatorId(), row.getInitiatorName(),
                    row.getInstanceCreatedAt(), row.getCurrentNodeSeq(), row.getInstanceStatus(),
                    row.getSubStatus(), row.getCcCreatedAt(), row.getCcSource(), row.getReadAt(),
                    row.getReadAt() != null));
        }
        return new PageResult<>(items, total, p, s, PageResult.DATE_FIELD_CREATED_AT);
    }

    // ================================================================ 动作（一律委托引擎）

    /** 通过（可勾选协同部门，仅 ② 生效）。 */
    @Transactional
    public ActionResult approve(Long taskId, String opinion, List<Long> collabDeptIds) {
        return engine.approve(taskId, opinion, collabDeptIds);
    }

    /** 驳回（意见 ≥5 字）。 */
    @Transactional
    public ActionResult reject(Long taskId, String opinion) {
        return engine.reject(taskId, opinion);
    }

    /** ⑦ 归档登记（仅登记不审批）。 */
    @Transactional
    public ActionResult archiveRegister(Long taskId, String opinion) {
        return engine.archiveRegister(taskId, opinion);
    }

    /** 回退上一已完成节点。 */
    @Transactional
    public ActionResult rollback(Long taskId, String reason) {
        return engine.rollback(taskId, reason);
    }

    /** 流转到指定承接部门。 */
    @Transactional
    public ActionResult route(Long taskId, Long toDeptId, String reason) {
        return engine.route(taskId, toDeptId, reason);
    }

    /** 回到本部门。 */
    @Transactional
    public ActionResult backHome(Long taskId, String reason) {
        return engine.backHome(taskId, reason);
    }

    /** 自由跳转（节点开关默认关闭）。 */
    @Transactional
    public ActionResult jump(Long taskId, Integer targetSeq, String reason) {
        return engine.jump(taskId, targetSeq, reason);
    }

    /** 加签（前/后）。 */
    @Transactional
    public ActionResult addSign(Long taskId, String addSignType, Long delegateUserId, String reason) {
        return engine.addSign(taskId, addSignType, delegateUserId, reason);
    }

    /** 请求补件。 */
    @Transactional
    public ActionResult supplementRequest(Long taskId, String reason) {
        return engine.supplementRequest(taskId, reason);
    }

    /** 转办。 */
    @Transactional
    public ActionResult transfer(Long taskId, Long toUserId, String reason) {
        return engine.transfer(taskId, toUserId, reason);
    }

    /** 改派（仅系统管理员）。 */
    @Transactional
    public ActionResult reassign(Long taskId, Long toUserId, String reason) {
        return engine.reassign(taskId, toUserId, reason);
    }

    // ================================================================ 内部

    private static List<TaskListItemView> toItems(List<FlowTaskViewRow> rows) {
        List<TaskListItemView> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (FlowTaskViewRow row : rows) {
            items.add(new TaskListItemView(
                    row.getTaskId(), row.getInstanceId(), row.getBizNo(), row.getFormType(), row.getCategory(),
                    row.getNodeSeq() == null && row.getCurrentNodeSeq() != null
                            ? row.getCurrentNodeSeq() : row.getNodeSeq(),
                    row.getNodeName(), row.getAssigneeId(), row.getOriginAssigneeId(), row.getAddSignType(),
                    row.getTaskStatus(),
                    row.getTaskStatus() == null ? null
                            : TaskStatus.of(row.getTaskStatus()).map(TaskStatus::label)
                                    .orElse(row.getTaskStatus()),
                    row.getOpinion(), row.getTaskCreatedAt(), row.getDecidedAt(),
                    row.getInitiatorId(), row.getInitiatorName(), row.getCurrentNodeSeq(),
                    row.getInstanceStatus(), row.getSubStatus(),
                    row.getTitle(),
                    row.getInstanceCreatedAt()));
        }
        return items;
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static int offset(int page, int size) {
        return (page - 1) * size;
    }

    /** 显式拒绝对空 id 的调用（避免「全表查询」这类静默降级）。 */
    static Long requireId(Long id, String name) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, name + " 不能为空");
        }
        return id;
    }
}
