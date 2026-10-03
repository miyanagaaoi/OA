package com.oa.workflow.task.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.ActionResult;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.PageResult;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.TaskListItemView;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.AddSignRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.ApproveRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.ArchiveRegisterRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.HandoverRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.JumpRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.RejectRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.ReasonRequest;
import com.oa.workflow.runtime.api.dto.RuntimeRequests.RouteRequest;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.task.app.FlowTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务与决议接口（2a.5）。
 *
 * <h2>路由清单（全部在 {@code /api/v1} 之下）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>权限码</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>{@code /flow-tasks/todo}</td><td>{@code flow}</td><td><b>待办</b>（pending 且我是处理人；分页 + 数据域）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-tasks/done}</td><td>{@code flow}</td><td><b>已办</b>（我处理过的任务）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-tasks/initiated}</td><td>{@code flow}</td><td><b>我发起的</b></td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/approve}</td><td>{@code flow:task:approve}</td><td>通过（② 可勾选协同部门）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/reject}</td><td>{@code flow:task:reject}</td><td>驳回（意见 ≥5 字）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/archive-register}</td><td>{@code flow:task:approve}</td><td>⑦ 归档登记（不产生审批决议）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/rollback}</td><td>{@code flow:task:rollback}</td><td>回退上一节点（Q6 预算 + 同节点 ≤2）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/route}</td><td>{@code flow:task:route}</td><td>流转（禁止回流）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/back-home}</td><td>{@code flow:task:route}</td><td>回到本部门（连续 ≤2，不计入 Q6）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/jump}</td><td>{@code flow:task:route}</td><td>自由跳转（节点开关默认关闭）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/add-sign}</td><td>{@code flow:task:addsign}</td><td>加签（前/后）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/supplement-request}</td><td>{@code flow:supplement:request}</td><td>请求补件（同节点 ≤1；Q6 全单预算）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/transfer}</td><td>{@code flow:task:transfer}</td><td>转办</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-tasks/{taskId}/reassign}</td><td>{@code flow:task:reassign}</td><td>改派（仅系统管理员）</td></tr>
 * </table>
 *
 * <h2>两层权限（纵深防御；与 {@code FlowRuntimeController} 同一范式）</h2>
 * <p><b>入口层只判权限</b>：每个动作端点取**该动作自己的权限码** {@code FlowAction.XXX.permission()}
 * （单一真源，见 {@link FlowAction}），不持有该码 → 403 / {@code 40301}，请求**不进引擎**、也不读实例。
 * 改前 11 个动作端点用 {@code WorkflowPermissionService#requireInitiator}
 * （放行 {@code flow} ∪ {@code admin:flow}），比引擎宽：{@code company_admin} 这类
 * 「持有 {@code admin:flow} 但不持动作码」的账号会「先过入口、再被引擎 403」，层次不干净。
 *
 * <p><b>引擎层判身份 + 状态机</b>（{@code FlowEngineService}）：同一动作码复核一次，
 * 再按 {@code flow_task.assignee_id} 判「任务本人」、按实例/节点/任务状态判可达性 ——
 * 入口放行不等于业务放行。「有动作权限但不是任务本人」在引擎层被拒，是**可接受的第二层**：
 * 入口层刻意不读实例，否则要么在闸门里引入数据库依赖，入口拒人发生在读库之后，
 * 要么用「403 / 域外 404」的差异泄露任务是否存在。
 *
 * <p>控制器只做入参绑定与「收单前权限」判定；状态迁移全部在 {@code FlowEngineService}（引擎层统一）。
 */
@RestController
@RequestMapping("/api/v1/flow-tasks")
public class FlowTaskController {

    private final FlowTaskService taskService;
    private final WorkflowPermissionService permissionService;

    public FlowTaskController(FlowTaskService taskService, WorkflowPermissionService permissionService) {
        this.taskService = taskService;
        this.permissionService = permissionService;
    }

    // ================================================================ 列表

    @GetMapping("/todo")
    public ApiResponse<PageResult<TaskListItemView>> todo(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        permissionService.requireInitiator("查看我的待办");
        return ApiResponse.success(taskService.todo(page, size));
    }

    @GetMapping("/done")
    public ApiResponse<PageResult<TaskListItemView>> done(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        permissionService.requireInitiator("查看我已办");
        return ApiResponse.success(taskService.done(page, size));
    }

    @GetMapping("/initiated")
    public ApiResponse<PageResult<TaskListItemView>> initiated(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        permissionService.requireInitiator("查看我发起的");
        return ApiResponse.success(taskService.initiated(page, size));
    }

    // ================================================================ 动作

    @PostMapping("/{taskId}/approve")
    @Audited(action = "approve", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> approve(@PathVariable("taskId") Long taskId,
                                             @RequestBody(required = false) ApproveRequest request) {
        permissionService.requirePermission(FlowAction.APPROVE.label(), FlowAction.APPROVE.permission());
        return ApiResponse.success(taskService.approve(taskId,
                request == null ? null : request.opinion(),
                request == null ? null : request.collabDeptIds()));
    }

    @PostMapping("/{taskId}/reject")
    @Audited(action = "reject", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> reject(@PathVariable("taskId") Long taskId,
                                            @RequestBody(required = false) RejectRequest request) {
        permissionService.requirePermission(FlowAction.REJECT.label(), FlowAction.REJECT.permission());
        return ApiResponse.success(taskService.reject(taskId, request == null ? null : request.opinion()));
    }

    @PostMapping("/{taskId}/archive-register")
    @Audited(action = "archive_register", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> archiveRegister(@PathVariable("taskId") Long taskId,
                                                     @RequestBody(required = false) ArchiveRegisterRequest request) {
        permissionService.requirePermission(FlowAction.ARCHIVE_REGISTER.label(), FlowAction.ARCHIVE_REGISTER.permission());
        return ApiResponse.success(taskService.archiveRegister(taskId,
                request == null ? null : request.opinion()));
    }

    @PostMapping("/{taskId}/rollback")
    @Audited(action = "rollback", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> rollback(@PathVariable("taskId") Long taskId,
                                              @Valid @RequestBody ReasonRequest request) {
        permissionService.requirePermission(FlowAction.ROLLBACK.label(), FlowAction.ROLLBACK.permission());
        return ApiResponse.success(taskService.rollback(taskId, request.reason()));
    }

    @PostMapping("/{taskId}/route")
    @Audited(action = "route", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> route(@PathVariable("taskId") Long taskId,
                                           @Valid @RequestBody RouteRequest request) {
        permissionService.requirePermission(FlowAction.ROUTE.label(), FlowAction.ROUTE.permission());
        return ApiResponse.success(taskService.route(taskId, request.toDeptId(), request.reason()));
    }

    @PostMapping("/{taskId}/back-home")
    @Audited(action = "back_home", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> backHome(@PathVariable("taskId") Long taskId,
                                              @Valid @RequestBody ReasonRequest request) {
        permissionService.requirePermission(FlowAction.BACK_HOME.label(), FlowAction.BACK_HOME.permission());
        return ApiResponse.success(taskService.backHome(taskId, request.reason()));
    }

    @PostMapping("/{taskId}/jump")
    @Audited(action = "jump", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> jump(@PathVariable("taskId") Long taskId,
                                          @Valid @RequestBody JumpRequest request) {
        permissionService.requirePermission(FlowAction.JUMP.label(), FlowAction.JUMP.permission());
        return ApiResponse.success(taskService.jump(taskId, request.targetSeq(), request.reason()));
    }

    @PostMapping("/{taskId}/add-sign")
    @Audited(action = "add_sign", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> addSign(@PathVariable("taskId") Long taskId,
                                             @Valid @RequestBody AddSignRequest request) {
        permissionService.requirePermission(FlowAction.ADD_SIGN.label(), FlowAction.ADD_SIGN.permission());
        return ApiResponse.success(taskService.addSign(taskId, request.type(), request.delegateUserId(),
                request.reason()));
    }

    @PostMapping("/{taskId}/supplement-request")
    @Audited(action = "supplement_request", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> supplementRequest(@PathVariable("taskId") Long taskId,
                                                       @Valid @RequestBody ReasonRequest request) {
        permissionService.requirePermission(FlowAction.SUPPLEMENT_REQUEST.label(), FlowAction.SUPPLEMENT_REQUEST.permission());
        return ApiResponse.success(taskService.supplementRequest(taskId, request.reason()));
    }

    @PostMapping("/{taskId}/transfer")
    @Audited(action = "transfer", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> transfer(@PathVariable("taskId") Long taskId,
                                              @Valid @RequestBody HandoverRequest request) {
        permissionService.requirePermission(FlowAction.TRANSFER.label(), FlowAction.TRANSFER.permission());
        return ApiResponse.success(taskService.transfer(taskId, request.toUserId(), request.reason()));
    }

    @PostMapping("/{taskId}/reassign")
    @Audited(action = "reassign", targetType = "task", targetId = "#taskId", recordArgs = true)
    public ApiResponse<ActionResult> reassign(@PathVariable("taskId") Long taskId,
                                              @Valid @RequestBody HandoverRequest request) {
        permissionService.requirePermission(FlowAction.REASSIGN.label(), FlowAction.REASSIGN.permission());
        return ApiResponse.success(taskService.reassign(taskId, request.toUserId(), request.reason()));
    }
}
