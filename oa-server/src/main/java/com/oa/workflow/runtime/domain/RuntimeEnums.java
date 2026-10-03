package com.oa.workflow.runtime.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 运行时状态机的**值域单一落点**（2a.4）—— 三层状态 + 轨迹动作 + 流转动作 + 加签类型。
 *
 * <h2>权威源（改这里之前必须先改文档）</h2>
 * <ul>
 *   <li>{@link InstanceStatus} / {@link SubStatus}：doc/enums.md §4（含 §4.1「回到草稿规则」）；</li>
 *   <li>{@link NodeStatus}：doc/enums.md §5「节点实例状态」；</li>
 *   <li>{@link TaskStatus}：doc/enums.md §6「任务状态」；</li>
 *   <li>{@link ThreadAction}：doc/enums.md §9「审批轨迹动作」—— 13 + 3 = <b>16</b> 个值
 *       （**没有** {@code jump} / {@code reopen} 值：跳转在轨迹上落 {@code skip}，
 *       档案口径见 {@link FlowAction#JUMP()}）；</li>
 *   <li>{@link RoutingAction}：doc/enums.md §7「流转动作」（{@code route} / {@code rollback} / {@code back_home}，
 *       旧值 {@code return_node} 作废）；</li>
 *   <li>{@link AddSignType}：doc/data-model.md §5.3 {@code flow_task.add_sign_type} 的 CHECK（{@code pre} / {@code post}）。</li>
 * </ul>
 *
 * <p><b>列名与值域以 DDL 为准</b>（doc/data-model.md §5.1–§5.5）：本类只做「值 → 语义」的映射，
 * 不做任何字符串拼接；CHECK 约束里的值域与本类一一对应（由 {@code RuntimeEnumsTest} 对 DDL 断言）。
 *
 * <p>「终态」语义（各枚举的 {@code terminal()}）是本类被引擎大量使用的判定：
 * 节点终态 = {@code approved/rejected/skipped/cancelled}；任务全部状态均为终态（enums.md §6 明示），
 * 因此任务侧用 {@code status != PENDING} 表达「已决议」。
 */
public final class RuntimeEnums {

    private RuntimeEnums() {
    }

    // ================================================================ 实例状态

    /** {@code flow_instance.status}（doc/enums.md §4.1）。 */
    public enum InstanceStatus {

        /** 草稿：未提交；全部字段可写。 */
        DRAFT("草稿", false),
        /** 审批中：已提交；**待补件期间主状态仍是 approving**。 */
        APPROVING("审批中", false),
        /** 已通过：全部节点通过（含⑦登记完成）；终态。 */
        APPROVED("已通过", true),
        /** 已驳回：任一节点驳回，回到发起人；可回到草稿重提。 */
        REJECTED("已驳回", true),
        /** 已撤回：**瞬时态**，写入轨迹与审计后立即回到 {@code draft}（data-model.md §8.2）。 */
        WITHDRAWN("已撤回", true),
        /** 已终止：仅系统管理员与集团分管领导；不可再提交。 */
        TERMINATED("已终止", true);

        private final String label;
        private final boolean terminal;

        InstanceStatus(String label, boolean terminal) {
            this.label = label;
            this.terminal = terminal;
        }

        public String label() {
            return label;
        }

        /** 是否终态（{@code approved/rejected/terminated}；{@code withdrawn} 为到达态但立即回草稿）。 */
        public boolean terminal() {
            return terminal;
        }

        /** 是否可被发起人继续编辑重提（草稿 / 已驳回 / 已撤回）。 */
        public boolean editableByInitiator() {
            return this == DRAFT || this == REJECTED || this == WITHDRAWN;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<InstanceStatus> of(String code) {
            return byCode(values(), code);
        }
    }

    /** {@code flow_instance.sub_status}（doc/enums.md §4.2）：{@code NULL} 表示无子状态。 */
    public enum SubStatus {

        /** 待补件：仅发起人可补附件与备注；主状态仍为 {@code approving}。 */
        PENDING_SUPPLEMENT("待补件");

        private final String label;

        SubStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<SubStatus> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 节点实例状态

    /** {@code flow_node_instance.status}（doc/enums.md §5）。 */
    public enum NodeStatus {

        /** 未开始：尚未轮到本节点。 */
        PENDING("未开始", true),
        /** 进行中：已产生任务，等待审批人决议。 */
        ACTIVE("进行中", false),
        /** 等待补件：本节点请求了补件，暂停且**不可审批**；补件提交后回到 {@code active}。 */
        WAITING_SUPPLEMENT("等待补件", false),
        /** 已通过。 */
        APPROVED("已通过", true),
        /** 已驳回：任意候选人驳回（会签亦同）。 */
        REJECTED("已驳回", true),
        /** 已跳过：**仅事项单②**（{@code involve_cost=false}）与自由跳转跳过的节点。 */
        SKIPPED("已跳过", true),
        /** 已退回：**非终态** —— 本节点被下一节点「回退上一节点」暂停，等上一节点重审通过后回到 {@code active}。 */
        RETURNED("已退回", false),
        /** 已取消：实例进入终态时，其余未完成节点统一取消。 */
        CANCELLED("已取消", true);

        private final String label;
        private final boolean finished;

        NodeStatus(String label, boolean finished) {
            this.label = label;
            this.finished = finished;
        }

        public String label() {
            return label;
        }

        /** 是否「不再推进」（可参与「同序号节点是否全部完成」的判定）。 */
        public boolean finished() {
            return finished;
        }

        /** 是否处于活动态（可产生/持有待办）。 */
        public boolean live() {
            return this == ACTIVE || this == WAITING_SUPPLEMENT;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<NodeStatus> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 任务状态

    /** {@code flow_task.status}（doc/enums.md §6）。**全部状态均为终态**，仅 {@link #PENDING} 可动作。 */
    public enum TaskStatus {

        /** 待处理（待办列表的默认状态）。 */
        PENDING("待处理"),
        /** 已同意。 */
        AGREED("已同意"),
        /** 已拒绝（意见必填且 ≥5 字）。 */
        REJECTED("已拒绝"),
        /** 已转办：原审批人失去该任务，新任务在受让人名下。 */
        TRANSFERRED("已转办"),
        /** 已改派（仅系统管理员）。 */
        REASSIGNED("已改派"),
        /** 已加签：本人的处理位被加签流程接管。 */
        ADDED_SIGN("已加签"),
        /** 已流转：指定下一承接部门。 */
        ROUTED("已流转"),
        /** 已回退上一节点。 */
        ROLLED_BACK("已回退"),
        /** 已请求补件：该任务不可再审批。 */
        SUPPLEMENT_REQUESTED("已请求补件"),
        /** 已自动关闭：同节点他人已决议、或单据已驳回/撤回/终止时系统关闭。 */
        AUTO_CLOSED("已自动关闭");

        private final String label;

        TaskStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<TaskStatus> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 轨迹动作（16 值）

    /** {@code sys_thread.action}（doc/enums.md §9）：13 + 3 = **16** 个定稿值。 */
    public enum ThreadAction {

        /** {@code submit}：发起人提交（含驳回后重新提交）。 */
        SUBMIT("提交"),
        /** {@code approve}：节点/任务通过。 */
        APPROVE("通过"),
        /** {@code reject}：驳回（意见 ≥5 字）。 */
        REJECT("驳回"),
        /** {@code route}：流转给下一承接部门。 */
        ROUTE("流转"),
        /** {@code rollback}：回退上一已完成节点。 */
        ROLLBACK("回退上一节点"),
        /** {@code back_home}：流转收束回本部门。 */
        BACK_HOME("回到本部门"),
        /** {@code supplement_request}：请求补件。 */
        SUPPLEMENT_REQUEST("请求补件"),
        /** {@code supplement_submit}：发起人提交补件。 */
        SUPPLEMENT_SUBMIT("提交补件"),
        /** {@code transfer}：审批人转办本人任务。 */
        TRANSFER("转办"),
        /** {@code reassign}：系统管理员改派。 */
        REASSIGN("改派"),
        /** {@code add_sign}：前加签 / 后加签。 */
        ADD_SIGN("加签"),
        /** {@code withdraw}：发起人在②通过前撤回。 */
        WITHDRAW("撤回"),
        /** {@code terminate}：管理员 / 集团分管领导终止。 */
        TERMINATE("终止"),
        /** {@code skip}：节点被跳过（仅事项单②；自由跳转亦落本值）。 */
        SKIP("跳过"),
        /** {@code archive_register}：⑦ 归档登记（**仅留痕、不产生审批决议**；旧值 {@code archive} 作废）。 */
        ARCHIVE_REGISTER("归档登记"),
        /** {@code cc}：抄送记录（**不产生审批决议、不产生待办**）。 */
        CC("抄送");

        private final String label;

        ThreadAction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<ThreadAction> of(String code) {
            return byCode(values(), code);
        }

        /** 16 个定稿值的码集合（用于「值域一致性」断言）。 */
        public static java.util.Set<String> codes() {
            java.util.Set<String> codes = new java.util.LinkedHashSet<>();
            for (ThreadAction action : values()) {
                codes.add(action.code());
            }
            return java.util.Collections.unmodifiableSet(codes);
        }
    }

    // ================================================================ 流转动作

    /** {@code flow_routing.action_type}（doc/enums.md §7；旧值 {@code return_node} 作废）。 */
    public enum RoutingAction {

        /** 流转：指定下一个承接部门；禁止回流到已处理部门。 */
        ROUTE("流转", true),
        /** 回退上一节点：退回上一个已完成节点重审。 */
        ROLLBACK("回退上一节点", true),
        /** 回到本部门：**不计入** {@code routing_count}（data-model.md §8.2）。 */
        BACK_HOME("回到本部门", false);

        private final String label;
        private final boolean countedInRoutingCount;

        RoutingAction(String label, boolean countedInRoutingCount) {
            this.label = label;
            this.countedInRoutingCount = countedInRoutingCount;
        }

        public String label() {
            return label;
        }

        /** 是否计入 {@code flow_instance.routing_count}（Q6 的 {@code maxReturnCount} 口径）。 */
        public boolean countedInRoutingCount() {
            return countedInRoutingCount;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<RoutingAction> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 加签类型

    /** {@code flow_task.add_sign_type}（doc/data-model.md §5.3 CHECK）：{@code pre} / {@code post}。 */
    public enum AddSignType {

        /** 前加签：加签人先审，审完回到本人。 */
        PRE("前加签"),
        /** 后加签：本人审完加签人再审。 */
        POST("后加签");

        private final String label;

        AddSignType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<AddSignType> of(String code) {
            return byCode(values(), code);
        }
    }

    // ================================================================ 内部

    private static <E extends Enum<E>> Optional<E> byCode(E[] values, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (E value : values) {
            if (value.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
