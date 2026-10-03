package com.oa.workflow.runtime.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.workflow.approver.api.dto.ApproverDtos.InstanceView;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.ActionCatalogView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.CcView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.InstanceRuntimeView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.NodeInstanceView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.RoutingView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.SupplementView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.TaskView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.ThreadView;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.CcRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.ReasonRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.SupplementSubmitRequest;
import com.oa.workflow.runtime.app.FlowEngineService;
import com.oa.workflow.runtime.app.FlowRuntimeQueryService;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.runtime.domain.FlowLinkage;
import com.oa.workflow.task.domain.ApprovalOpinionPolicy;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时接口（2a.4）：实例状态机动作 + 运行态只读视图。
 *
 * <h2>路由清单（全部在 {@code /api/v1} 之下）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>{@code /flow-actions}</td><td><b>动作面清单</b>（动作 → 权限码 / 必填原因 / 意见下限 / 轨迹动作 / 状态迁移）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-linkage-rules}</td><td><b>§7.2 联动规则书</b>（触发 → 联动结果，逐行可核对）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-opinion-rules}</td><td>意见 / 原因校验规则（驳回 ≥5 字等）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/reopen}</td><td>回到草稿（驳回/撤回后；<b>入口闸门 = {@code FlowAction.REOPEN.permission()} = {@code flow}</b>）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/resubmit}</td><td><b>重提</b>：重新解析快照与版本 → 回草稿 → 提交（<b>入口闸门 = {@code FlowAction.SUBMIT.permission()} = {@code flow}</b>）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/withdraw}</td><td>撤回（仅发起人本人或系统管理员；仅节点②通过前；<b>入口闸门 = {@code flow:task:withdraw}</b>，与引擎同源）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/terminate}</td><td>终止（系统管理员 / 集团分管领导；<b>入口闸门 = AC-49 专用判据</b>，见下）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/supplement}</td><td>提交补件（仅发起人；<b>入口闸门 = {@code FlowAction.SUPPLEMENT_SUBMIT.permission()} = {@code flow}</b>）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/cc}</td><td>抄送登记（不产生待办、不参与决议；<b>入口闸门 = {@code flow}</b>，与引擎同源）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/runtime}</td><td><b>运行态总览</b>（三层状态 + 轨迹 + 流转链 + 补件 + 抄送 + Q6 剩余）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/node-instances}</td><td>节点实例清单</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/task-list}</td><td>该单全部任务</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/thread}</td><td>审批轨迹（16 值动作）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/routing}</td><td>流转链</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/supplements}</td><td>补件记录（含 Q7 应完成时间）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/cc}</td><td>抄送记录</td></tr>
 * </table>
 *
 * <h2>两层权限（纵深防御，不要合并）</h2>
 * <p><b>分工</b>：入口层只判**权限**（不读实例、不碰 mapper —— 拒人必须在读库之前），
 * 引擎层判**身份 + 状态机 + 闸门**（发起人本人由引擎按 {@code instance.initiatorId} 判）。
 * 「入口放行」只说明这个账号有资格执行该类动作，不说明这张单归他管。
 *
 * <p><b>入口闸门的取值口径（2026-10-04 收敛）</b>：一律取**该动作自己的权限码**
 * {@code FlowAction.XXX.permission()}（单一真源，见 {@link FlowAction}），
 * **不再**用 {@code requireInitiator}（它放行 {@code flow} ∪ {@code admin:flow}，
 * 比动作面宽：持 {@code admin:flow} 的 {@code company_admin} 会「先过入口、再被引擎 403」）。
 * <ol>
 *   <li><b>入口层</b>（本控制器）：
 *     <ul>
 *       <li>{@code /reopen} → {@code FlowAction.REOPEN.permission()}（{@code flow}）；
 *           {@code /resubmit} → {@code FlowAction.SUBMIT.permission()}（{@code flow}）；
 *           {@code /supplement} → {@code FlowAction.SUPPLEMENT_SUBMIT.permission()}（{@code flow}）；</li>
 *       <li>{@code /withdraw} → {@code FlowAction.WITHDRAW.permission()}（{@code flow:task:withdraw}），
 *           与 {@code FlowEngineService#withdraw} 同源同参；</li>
 *       <li>{@code /cc} → {@code FlowAction.CC.permission()}（{@code flow}），与引擎同源同参；</li>
 *       <li>{@code /terminate} 用 <b>AC-49 专用闸门</b>
 *           {@code WorkflowPermissionService#requireTerminate}（系统管理员 ∪ {@code group_leader} 角色 ∪
 *           {@code flow:task:terminate} 权限）—— 因为 {@code flow} 是门户基础权限，
 *           {@code employee} 经祖先闭包也持有，不能代表「终止」的资格；</li>
 *       <li>只读视图（{@code /runtime}、{@code /node-instances}、{@code /task-list}、{@code /thread}、
 *           {@code /routing}、{@code /supplements}、{@code /cc} 的 GET）**不设入口闸门**，
 *           仅靠数据域织入（域外实例一律 404）—— 加粗粒度 {@code flow} 闸门会误伤本该可读的
 *           {@code admin:flow} 主体。</li>
 *     </ul></li>
 *   <li><b>引擎层</b>（{@code FlowEngineService}）：动作级权限码按 {@code FlowAction.permission()} 复核，
 *       并做发起人身份（{@code requireInitiatorOrAdmin}）、状态机、闸门计数等业务判定 ——
 *       入口放行不等于业务放行。**发起人身份只在引擎层判**：非发起人但持有动作权限者在引擎层被拒，
 *       这是可接受的第二层（入口层不读实例，因此不会用 403/404 的差异泄露实例是否存在）。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1")
public class FlowRuntimeController {

    private final FlowEngineService engine;
    private final FlowRuntimeQueryService queryService;
    private final WorkflowPermissionService permissionService;

    public FlowRuntimeController(FlowEngineService engine,
                                 FlowRuntimeQueryService queryService,
                                 WorkflowPermissionService permissionService) {
        this.engine = engine;
        this.queryService = queryService;
        this.permissionService = permissionService;
    }

    // ================================================================ 规则回显

    /** 动作面清单（动作 → 权限 / 必须原因 / 意见下限 / 轨迹动作 / 状态迁移）。 */
    @GetMapping("/flow-actions")
    public ApiResponse<List<ActionCatalogView>> actions() {
        permissionService.requireInitiator("查看流程动作面");
        List<ActionCatalogView> views = new ArrayList<>();
        for (FlowAction action : FlowEngineService.actionCatalog()) {
            views.add(new ActionCatalogView(action.code(), action.label(), action.permission(),
                    action.requiresReason(), action.minOpinionChars(),
                    action.taskStatusAfter() == null ? null : action.taskStatusAfter().code(),
                    action.threadAction() == null ? null : action.threadAction().code(),
                    action.transition()));
        }
        return ApiResponse.success(views);
    }

    /** §7.2 联动规则书（PRD 表格的机器可读形态）。 */
    @GetMapping("/flow-linkage-rules")
    public ApiResponse<List<Map<String, String>>> linkageRules() {
        permissionService.requireInitiator("查看流程联动规则");
        List<Map<String, String>> views = new ArrayList<>();
        for (FlowLinkage.Rule rule : FlowLinkage.rules()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("trigger", rule.trigger());
            item.put("consequence", rule.consequence());
            item.put("source", rule.source());
            views.add(item);
        }
        return ApiResponse.success(views);
    }

    /** 意见 / 原因校验规则。 */
    @GetMapping("/flow-opinion-rules")
    public ApiResponse<List<String>> opinionRules() {
        permissionService.requireInitiator("查看意见校验规则");
        return ApiResponse.success(ApprovalOpinionPolicy.rules());
    }

    // ================================================================ 实例级动作

    /** 回到草稿（驳回/撤回后编辑；重提时会重新解析快照）。 */
    @PostMapping("/flow-instances/{instanceId}/reopen")
    @Audited(action = "reopen", targetType = "instance", targetId = "#instanceId", recordAfter = false)
    public ApiResponse<InstanceView> reopen(@PathVariable("instanceId") Long instanceId) {
        permissionService.requirePermission(FlowAction.REOPEN.label(), FlowAction.REOPEN.permission());
        return ApiResponse.success(engine.reopen(instanceId));
    }

    /** 重提：重新解析审批人快照与流程版本（REQ-FLOW-017），然后提交。 */
    @PostMapping("/flow-instances/{instanceId}/resubmit")
    @Audited(action = "submit", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<InstanceView> resubmit(@PathVariable("instanceId") Long instanceId,
                                             @RequestBody(required = false) ReasonRequest request) {
        permissionService.requirePermission(FlowAction.SUBMIT.label(), FlowAction.SUBMIT.permission());
        return ApiResponse.success(engine.resubmit(instanceId,
                request == null ? null : request.reason()));
    }

    /**
     * 撤回（仅发起人本人或系统管理员；仅节点②通过前；撤回后回草稿）。
     *
     * <p><b>入口闸门 = 动作面权限码本身</b>（{@link FlowAction#WITHDRAW} 的
     * {@code flow:task:withdraw}，与引擎 {@code FlowEngineService#withdraw} 同源同参），
     * 不再是 {@code requireInitiator}（放行 {@code flow} 或 {@code admin:flow}）。
     *
     * <p><b>权限判定与身份判定分清</b>（两道闸门各管一件事）：
     * <ol>
     *   <li><b>入口层（本方法）判权限</b>：不持有 {@code flow:task:withdraw} → 403 / {@code 40301}
     *       （{@code requiredPermissions}），请求**不进入引擎**。改前 {@code requireInitiator} 会放行
     *       {@code admin:flow}（{@code company_admin} 正是此类：持 {@code admin:flow} 一族、
     *       但不持 {@code flow} 与 {@code flow:task:withdraw}），而引擎只认
     *       {@code flow:task:withdraw} —— 入口比引擎宽，该账号「先过入口、再被引擎 403」；</li>
     *   <li><b>引擎层判身份</b>：{@code "发起人本人或系统管理员"} 的语义由
     *       {@code FlowEngineService#withdraw} 按 {@code instance.initiatorId} + {@code isSuperAdmin} 判
     *       （引擎侧一行未改）。「有该权限但不是发起人」者在此被拒 —— <b>可接受的第二层</b>：
     *       入口层刻意**不读实例**，否则要么在闸门里引入数据库依赖（拒人发生在读库之后），
     *       要么用「403 / 域外 404」的差异泄露实例是否存在。</li>
     * </ol>
     */
    @PostMapping("/flow-instances/{instanceId}/withdraw")
    @Audited(action = "withdraw", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<InstanceView> withdraw(@PathVariable("instanceId") Long instanceId,
                                             @Valid @RequestBody ReasonRequest request) {
        permissionService.requirePermission(FlowAction.WITHDRAW.label(), FlowAction.WITHDRAW.permission());
        return ApiResponse.success(engine.withdraw(instanceId, request.reason()));
    }

    /**
     * 终止（系统管理员 / 集团分管领导；必填原因；终态）。
     *
     * <p><b>入口闸门 = AC-49 自身口径</b>（不是 {@code requireInitiator}）：
     * {@code WorkflowPermissionService#requireTerminate} = 系统管理员 ∪ {@code group_leader} 角色
     * ∪ {@code flow:task:terminate} 权限。
     *
     * <p>改前这里是 {@code requireInitiator("终止流程")}（放行 {@code flow} 或 {@code admin:flow}）。
     * {@code flow} 是门户基础权限，{@code employee} 经祖先闭包也持有 —— 入口因此会放行一个
     * 本不该有入口的账号，拒人只剩引擎一层。现入口与引擎共用 AC-49 判据，**两层都在**：
     * <ol>
     *   <li><b>入口层</b>（本方法）：不持有该角色/权限 → 403 / {@code 40301}，
     *       「终止流程」仅系统管理员与集团分管领导可执行（AC-49 / REQ-FLOW-010）；</li>
     *   <li><b>引擎层</b>（{@code FlowEngineService#terminate}）：同一判据再判一次，兜住绕过控制器直调服务的情形。</li>
     * </ol>
     */
    @PostMapping("/flow-instances/{instanceId}/terminate")
    @Audited(action = "terminate", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<InstanceView> terminate(@PathVariable("instanceId") Long instanceId,
                                              @Valid @RequestBody ReasonRequest request) {
        permissionService.requireTerminate();
        return ApiResponse.success(engine.terminate(instanceId, request.reason()));
    }

    /** 提交补件（仅发起人；只补附件与备注，主字段只读）。 */
    @PostMapping("/flow-instances/{instanceId}/supplement")
    @Audited(action = "supplement", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<com.oa.workflow.runtime.api.dto.RuntimeDtos.ActionResult> supplementSubmit(
            @PathVariable("instanceId") Long instanceId,
            @RequestBody(required = false) SupplementSubmitRequest request) {
        permissionService.requirePermission(FlowAction.SUPPLEMENT_SUBMIT.label(),
                FlowAction.SUPPLEMENT_SUBMIT.permission());
        return ApiResponse.success(engine.supplementSubmit(instanceId,
                request == null ? null : request.note()));
    }

    /**
     * 抄送登记（只读可见，不产生待办、不参与决议）。
     *
     * <p><b>入口闸门 = {@code FlowAction.CC.permission()} = {@code flow}</b>，与引擎
     * {@code FlowEngineService#addCc} 同源同参。改前用 {@code requireInitiator}
     * （放行 {@code flow} 或 {@code admin:flow}）比引擎**宽**：{@code company_admin}（持 {@code admin:flow}）
     * 能过入口、随后被引擎按 {@code flow} 拒 —— 属「入口比引擎宽」的反向不一致，故收紧到与引擎逐字一致。
     *
     * <p>发起人身份（{@code requireInitiatorOrAdmin}）仍由引擎判，入口层不读实例（理由同 {@code withdraw}）。
     */
    @PostMapping("/flow-instances/{instanceId}/cc")
    @Audited(action = "cc", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<Map<String, Object>> cc(@PathVariable("instanceId") Long instanceId,
                                               @Valid @RequestBody CcRequest request) {
        permissionService.requirePermission(FlowAction.CC.label(), FlowAction.CC.permission());
        return ApiResponse.success(engine.addCc(instanceId, request.userIds()));
    }

    // ================================================================ 运行态只读视图

    @GetMapping("/flow-instances/{instanceId}/runtime")
    public ApiResponse<InstanceRuntimeView> runtime(@PathVariable("instanceId") Long instanceId) {
        return ApiResponse.success(queryService.runtime(instanceId));
    }

    @GetMapping("/flow-instances/{instanceId}/node-instances")
    public ApiResponse<List<NodeInstanceView>> nodeInstances(@PathVariable("instanceId") Long instanceId) {
        return ApiResponse.success(queryService.nodes(instanceId));
    }

    @GetMapping("/flow-instances/{instanceId}/task-list")
    public ApiResponse<List<TaskView>> taskList(@PathVariable("instanceId") Long instanceId) {
        return ApiResponse.success(queryService.tasks(instanceId));
    }

    @GetMapping("/flow-instances/{instanceId}/thread")
    public ApiResponse<List<ThreadView>> thread(@PathVariable("instanceId") Long instanceId) {
        return ApiResponse.success(queryService.thread(instanceId));
    }

    @GetMapping("/flow-instances/{instanceId}/routing")
    public ApiResponse<List<RoutingView>> routing(@PathVariable("instanceId") Long instanceId) {
        return ApiResponse.success(queryService.routing(instanceId));
    }

    @GetMapping("/flow-instances/{instanceId}/supplements")
    public ApiResponse<List<SupplementView>> supplements(@PathVariable("instanceId") Long instanceId) {
        return ApiResponse.success(queryService.supplements(instanceId));
    }

    @GetMapping("/flow-instances/{instanceId}/cc")
    public ApiResponse<List<CcView>> ccList(@PathVariable("instanceId") Long instanceId) {
        return ApiResponse.success(queryService.cc(instanceId));
    }
}
