package com.oa.workflow.runtime.domain;

import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * <b>动作面清单</b>（2a.4 / 2a.5）—— 每个动作的「权限码 / 是否必填原因 / 意见下限 / 任务后置状态 / 状态迁移」
 * 单一落点。引擎、控制器与出参全部从这里取，**不在别处硬编码权限码或状态迁移**。
 *
 * <h2>权限码来源</h2>
 * <p>全部取自既有权限种子 {@code oa-server/src/main/resources/db/migration/V4__permissions.sql}
 * （{@code flow} 与 {@code flow:task:*} 一族，见该文件第 169–229 行的 {@code sys_permission} 种子），
 * 因此**不需要**新增权限码：动作面与授权面天然对齐。
 *
 * <h2>轨迹动作的映射（17 值约束下的两个刻意选择）</h2>
 * <ol>
 *   <li><b>自由跳转</b>（{@link #JUMP}）：doc/enums.md §9 的 17 个定稿值里**没有</b> {@code jump}，
 *       而跳转的语义就是「跳过中间节点」，因此轨迹落 {@code skip}（{@link ThreadAction#SKIP}），
 *       理由文案里带走目标节点序号；审计日志另记 {@code jump}（{@code sys_log.action} 是自由文本列）。</li>
 *   <li><b>回到草稿</b>（驳回/撤回后重提，见 {@link #REOPEN}）：同样无对应轨迹值
 *       （重新提交本身落 {@code submit}），故仅写审计日志。</li>
 * </ol>
 *
 * <h2>与 PRD §6.3.1 / §6.4 的对应</h2>
 * <p>「通过 / 驳回 / 流转 / 回退上一节点 / 回到本部门 / 要求补充材料 / 终止」七项全部在册；
 * 「动态加签 / 自由跳转 / 撤回 / 转办 / 改派 / 归档登记 / 抄送」亦在册。
 */
public enum FlowAction {

    /** 提交（{@code draft → approving}）。 */
    SUBMIT("提交", "flow", ThreadAction.SUBMIT, false, 0, null,
            "draft → approving（首次提交或驳回后重提均走本动作）"),
    /** 通过（节点决议）。 */
    APPROVE("通过", "flow:task:approve", ThreadAction.APPROVE, false, 0, TaskStatus.AGREED,
            "任务 pending → agreed；节点达成决议条件后 → approved，实例推进"),
    /** 驳回（意见 ≥5 字；一期去向固定「回到发起人」）。 */
    REJECT("驳回", "flow:task:reject", ThreadAction.REJECT, true, 5, TaskStatus.REJECTED,
            "任务 pending → rejected；节点 → rejected；实例 → rejected（回到发起人）"),
    /** 回退上一已完成节点（同一节点被回退 ≤2；计入 routing_count 的 Q6 预算）。 */
    ROLLBACK("回退上一节点", "flow:task:rollback", ThreadAction.ROLLBACK, true, 0, TaskStatus.ROLLED_BACK,
            "任务 pending → rolled_back；当前节点 → returned；上一节点 → active（returned_count +1）"),
    /** 流转：指定下一承接部门（禁止回流；计入 routing_count 的 Q6 预算）。 */
    ROUTE("流转", "flow:task:route", ThreadAction.ROUTE, true, 0, TaskStatus.ROUTED,
            "任务 pending → routed；当前节点 → approved；承接部门负责人产生新任务"),
    /** 回到本部门（连续 ≤2；**不计入** routing_count）。 */
    BACK_HOME("回到本部门", "flow:task:route", ThreadAction.BACK_HOME, true, 0, TaskStatus.ROUTED,
            "任务 pending → routed；当前节点 → approved；收束回本部门并产生本部门任务"),
    /** 自由跳转（节点开关默认关闭；必须填写原因并记入审计与轨迹）。 */
    JUMP("自由跳转", "flow:task:route", ThreadAction.SKIP, true, 0, TaskStatus.ROUTED,
            "任务 pending → routed；中间节点 → skipped（轨迹 skip）；目标节点 → active"),
    /** 加签（前加签 / 后加签；节点开关控制）。 */
    ADD_SIGN("加签", "flow:task:addsign", ThreadAction.ADD_SIGN, true, 0, TaskStatus.ADDED_SIGN,
            "前加签：本人任务 → added_sign，加签人先审；后加签：本人已同意，加签人再审，均回到本人/继续决议"),
    /** 转办（本人任务转他人；同数据域可见性校验）。 */
    TRANSFER("转办", "flow:task:transfer", ThreadAction.TRANSFER, true, 0, TaskStatus.TRANSFERRED,
            "原任务 → transferred；受让人产生新的 pending 任务（origin_assignee_id 留痕）"),
    /** 改派（**仅系统管理员**；用于人员离职、快照审批人不可用）。 */
    REASSIGN("改派", "flow:task:reassign", ThreadAction.REASSIGN, true, 0, TaskStatus.REASSIGNED,
            "原任务 → reassigned；被改派人产生新的 pending 任务（origin_assignee_id 留痕）"),
    /** 请求补件（同节点 ≤1；受 Q6 全单补件预算约束）。 */
    SUPPLEMENT_REQUEST("请求补件", "flow:supplement:request", ThreadAction.SUPPLEMENT_REQUEST, true, 0,
            TaskStatus.SUPPLEMENT_REQUESTED,
            "任务 → supplement_requested；节点 → waiting_supplement；实例 sub_status → pending_supplement"),
    /** 提交补件（**仅发起人**；主字段只读，只补附件与备注）。 */
    SUPPLEMENT_SUBMIT("提交补件", "flow", ThreadAction.SUPPLEMENT_SUBMIT, false, 0, null,
            "实例 sub_status → NULL；补件记录 → submitted；节点 → active；任务回到请求补件的审批人"),
    /** 撤回（仅发起人；仅节点②通过前；撤回后回到草稿）。 */
    WITHDRAW("撤回", "flow:task:withdraw", ThreadAction.WITHDRAW, true, 0, null,
            "实例 → withdrawn（到达态）→ 立即回 draft；未完成节点 → cancelled；待决议任务 → auto_closed"),
    /** 终止（系统管理员或集团分管领导；终态）。 */
    TERMINATE("终止", "flow:task:terminate", ThreadAction.TERMINATE, true, 0, null,
            "实例 → terminated（终态）；未完成节点 → cancelled；待决议任务 → auto_closed"),
    /** ⑦ 归档登记（默认「仅登记不审批」）。 */
    ARCHIVE_REGISTER("归档登记", "flow:task:approve", ThreadAction.ARCHIVE_REGISTER, false, 0, TaskStatus.AGREED,
            "任务 pending → agreed（登记，不产生审批决议）；节点 → approved；若无后续节点则实例 → approved"),
    /** 抄送（只读可见；不产生待办、不产生决议）。 */
    CC("抄送", "flow", ThreadAction.CC, false, 0, null,
            "不产生任务、不产生节点实例、不参与决议；被抄送人只读可见"),
    /** 回到草稿（驳回/撤回后可重提；轨迹值域无 reopen，仅写审计日志）。 */
    REOPEN("回到草稿", "flow", null, false, 0, null,
            "rejected/withdrawn → draft（重提时重新解析审批人快照与流程版本）");

    private final String label;
    private final String permission;
    private final ThreadAction threadAction;
    private final boolean requiresReason;
    private final int minOpinionChars;
    private final TaskStatus taskStatusAfter;
    private final String transition;

    FlowAction(String label, String permission, ThreadAction threadAction, boolean requiresReason,
               int minOpinionChars, TaskStatus taskStatusAfter, String transition) {
        this.label = label;
        this.permission = permission;
        this.threadAction = threadAction;
        this.requiresReason = requiresReason;
        this.minOpinionChars = minOpinionChars;
        this.taskStatusAfter = taskStatusAfter;
        this.transition = transition;
    }

    public String label() {
        return label;
    }

    /** 所需权限码（取自 V4 权限种子；{@code flow} 为审批动作基础包）。 */
    public String permission() {
        return permission;
    }

    /** 轨迹动作（{@code sys_thread.action} 的 17 值之一；{@link #REOPEN} 为 {@code null}）。 */
    public ThreadAction threadAction() {
        return threadAction;
    }

    /** 是否必须填写原因/意见（流转、回退、终止、跳转、转办、改派、驳回均必填）。 */
    public boolean requiresReason() {
        return requiresReason;
    }

    /** 意见的最小字数（仅驳回为 5；其余 0 表示不设下限但必填非空）。 */
    public int minOpinionChars() {
        return minOpinionChars;
    }

    /** 任务在动作后的终态（实例级动作为 {@code null}）。 */
    public TaskStatus taskStatusAfter() {
        return taskStatusAfter;
    }

    /** 状态迁移说明（报告与出参共用同一份文案）。 */
    public String transition() {
        return transition;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<FlowAction> of(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (FlowAction action : values()) {
            if (action.code().equals(normalized)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    /** 全部动作（出参「动作面清单」用）。 */
    public static List<FlowAction> all() {
        return List.of(values());
    }
}
