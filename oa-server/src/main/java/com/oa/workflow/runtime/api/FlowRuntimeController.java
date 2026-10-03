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
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/reopen}</td><td>回到草稿（驳回/撤回后）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/resubmit}</td><td><b>重提</b>：重新解析快照与版本 → 回草稿 → 提交</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/withdraw}</td><td>撤回（仅发起人；仅节点②通过前）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/terminate}</td><td>终止（系统管理员 / 集团分管领导）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/supplement}</td><td>提交补件（仅发起人）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{id}/cc}</td><td>抄送登记（不产生待办、不参与决议）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/runtime}</td><td><b>运行态总览</b>（三层状态 + 轨迹 + 流转链 + 补件 + 抄送 + Q6 剩余）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/node-instances}</td><td>节点实例清单</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/task-list}</td><td>该单全部任务</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/thread}</td><td>审批轨迹（16 值动作）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/routing}</td><td>流转链</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/supplements}</td><td>补件记录（含 Q7 应完成时间）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{id}/cc}</td><td>抄送记录</td></tr>
 * </table>
 *
 * <p>权限：全部走 {@code flow} 或 {@code admin:flow}（{@code WorkflowPermissionService#requireInitiator}），
 * 叠加数据域织入（域外实例一律 404）；动作级权限码在引擎层按 {@code FlowAction.permission()} 校验。
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
        permissionService.requireInitiator("回到草稿");
        return ApiResponse.success(engine.reopen(instanceId));
    }

    /** 重提：重新解析审批人快照与流程版本（REQ-FLOW-017），然后提交。 */
    @PostMapping("/flow-instances/{instanceId}/resubmit")
    @Audited(action = "submit", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<InstanceView> resubmit(@PathVariable("instanceId") Long instanceId,
                                             @RequestBody(required = false) ReasonRequest request) {
        permissionService.requireInitiator("重新提交审批单");
        return ApiResponse.success(engine.resubmit(instanceId,
                request == null ? null : request.reason()));
    }

    /** 撤回（仅发起人；仅节点②通过前；撤回后回草稿）。 */
    @PostMapping("/flow-instances/{instanceId}/withdraw")
    @Audited(action = "withdraw", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<InstanceView> withdraw(@PathVariable("instanceId") Long instanceId,
                                             @Valid @RequestBody ReasonRequest request) {
        permissionService.requireInitiator("撤回审批单");
        return ApiResponse.success(engine.withdraw(instanceId, request.reason()));
    }

    /** 终止（系统管理员 / 集团分管领导；必填原因；终态）。 */
    @PostMapping("/flow-instances/{instanceId}/terminate")
    @Audited(action = "terminate", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<InstanceView> terminate(@PathVariable("instanceId") Long instanceId,
                                              @Valid @RequestBody ReasonRequest request) {
        permissionService.requireInitiator("终止流程");
        return ApiResponse.success(engine.terminate(instanceId, request.reason()));
    }

    /** 提交补件（仅发起人；只补附件与备注，主字段只读）。 */
    @PostMapping("/flow-instances/{instanceId}/supplement")
    @Audited(action = "supplement", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<com.oa.workflow.runtime.api.dto.RuntimeDtos.ActionResult> supplementSubmit(
            @PathVariable("instanceId") Long instanceId,
            @RequestBody(required = false) SupplementSubmitRequest request) {
        permissionService.requireInitiator("提交补件");
        return ApiResponse.success(engine.supplementSubmit(instanceId,
                request == null ? null : request.note()));
    }

    /** 抄送登记（只读可见，不产生待办、不参与决议）。 */
    @PostMapping("/flow-instances/{instanceId}/cc")
    @Audited(action = "cc", targetType = "instance", targetId = "#instanceId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<Map<String, Object>> cc(@PathVariable("instanceId") Long instanceId,
                                               @Valid @RequestBody CcRequest request) {
        permissionService.requireInitiator("抄送单据");
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
