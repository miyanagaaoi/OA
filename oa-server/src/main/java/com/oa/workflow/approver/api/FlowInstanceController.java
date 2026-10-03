package com.oa.workflow.approver.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.api.dto.ApproverDtos.CreateInstanceRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.InstanceView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckReportView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRuleView;
import com.oa.workflow.approver.api.dto.ApproverDtos.ReparseRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.ReparseView;
import com.oa.workflow.approver.api.dto.ApproverDtos.SubmitRequest;
import com.oa.workflow.approver.app.ApproverPrecheckService;
import com.oa.workflow.approver.app.FlowInstanceService;
import com.oa.workflow.approver.domain.ApproverSnapshot;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程实例发起与审批人快照接口（2a.3）。
 *
 * <h2>路由清单（全部在 {@code /api/v1} 之下；所需权限 {@code flow} 或 {@code admin:flow}）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/precheck}</td><td><b>发起前预检</b>（只读干跑）：逐节点解析候选人，任一为空即 {@code allowed=false} 并给出节点/规则/缺配</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/precheck/rules}</td><td>预检规则清单（已实现项与后续工作包负责项都显式列出）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances}</td><td>建草稿实例：预检通过 → <b>锁定模板版本</b> → 固化审批人快照</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{instanceId}/submit}</td><td>提交（{@code draft → approving}；仅此一条迁移，其余属 2a.4）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances}</td><td>实例列表（按模板 / 版本 / 状态 / 发起人过滤；数据域过滤）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{instanceId}}</td><td>实例详情（含快照）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{instanceId}/approver-snapshot}</td><td>读取作为运行时权威数据的快照（数据域过滤）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{instanceId}/approver-snapshot}</td><td>（重新）解析并固化快照（口径同 reparse）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{instanceId}/approver-snapshot/reparse}</td><td>驳回重提时按最新已发布版本重新解析，旧快照写审计日志</td></tr>
 * </table>
 *
 * <p><b>快照不可变性的范围</b>：建实例之后再改组织/负责人/模板，**不会**改变已有快照
 * （只有显式调用 reparse 才会重算，且旧快照进审计）。
 */
@RestController
@RequestMapping("/api/v1/flow-instances")
public class FlowInstanceController {

    private final FlowInstanceService instanceService;
    private final ApproverPrecheckService precheckService;
    private final WorkflowPermissionService permissionService;
    private final com.oa.workflow.runtime.app.FlowEngineService engineService;

    public FlowInstanceController(FlowInstanceService instanceService,
                                  ApproverPrecheckService precheckService,
                                  WorkflowPermissionService permissionService,
                                  com.oa.workflow.runtime.app.FlowEngineService engineService) {
        this.instanceService = instanceService;
        this.precheckService = precheckService;
        this.permissionService = permissionService;
        this.engineService = engineService;
    }

    // ================================================================ 预检

    /** 发起前预检（只读；{@code POST /flow-instances/precheck}）。 */
    @PostMapping("/precheck")
    public ApiResponse<PrecheckReportView> precheck(@RequestBody PrecheckRequest request) {
        CurrentUser principal = permissionService.requireInitiator("发起前预检");
        PrecheckReportView report = precheckService.precheck(request, principal);
        if (!report.allowed()) {
            precheckService.logBlocked(report, principal);
        }
        return ApiResponse.success(report);
    }

    /** 预检规则清单。 */
    @GetMapping("/precheck/rules")
    public ApiResponse<List<PrecheckRuleView>> precheckRules() {
        permissionService.requireInitiator("查看发起前拦截规则");
        return ApiResponse.success(ApproverPrecheckService.PRECHECK_RULES);
    }

    // ================================================================ 发起

    /** 建草稿实例（预检通过才会落库；同时锁定模板版本并固化快照）。 */
    @PostMapping
    @Audited(action = "create", targetType = "instance", recordArgs = true, recordAfter = false)
    public ApiResponse<InstanceView> create(@RequestBody CreateInstanceRequest request) {
        CurrentUser principal = permissionService.requireInitiator("发起审批单");
        return ApiResponse.success(instanceService.create(request, principal));
    }

    /** 提交（草稿 → 审批中；建节点实例 + 首个节点待办由运行时引擎完成）。 */
    @PostMapping("/{instanceId}/submit")
    @Audited(action = "submit", targetType = "instance", targetId = "#instanceId", recordArgs = true)
    public ApiResponse<InstanceView> submit(@PathVariable("instanceId") Long instanceId,
                                            @Valid @RequestBody SubmitRequest request) {
        permissionService.requireInitiator("提交审批单");
        return ApiResponse.success(engineService.submit(instanceId, request.reason()));
    }

    // ================================================================ 查询

    @GetMapping
    public ApiResponse<List<InstanceView>> list(
            @RequestParam(name = "templateId", required = false) Long templateId,
            @RequestParam(name = "templateVersion", required = false) Integer templateVersion,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "initiatorId", required = false) Long initiatorId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        permissionService.requireInitiator("查看审批单列表");
        return ApiResponse.success(instanceService.list(templateId, templateVersion, status, initiatorId, limit));
    }

    @GetMapping("/{instanceId}")
    public ApiResponse<InstanceView> detail(@PathVariable("instanceId") Long instanceId) {
        permissionService.requireInitiator("查看审批单");
        return ApiResponse.success(instanceService.detail(instanceId));
    }

    /** 读取快照（运行时权威数据；数据域过滤，域外按 404）。 */
    @GetMapping("/{instanceId}/approver-snapshot")
    public ApiResponse<ApproverSnapshot> snapshot(@PathVariable("instanceId") Long instanceId) {
        permissionService.requireInitiator("查看审批人快照");
        return ApiResponse.success(instanceService.snapshot(instanceId));
    }

    /** （重新）解析并固化快照（口径同 reparse；旧快照写审计日志）。 */
    @PostMapping("/{instanceId}/approver-snapshot")
    @Audited(action = "freeze_snapshot", targetType = "instance", targetId = "#instanceId", recordArgs = true)
    public ApiResponse<ReparseView> freezeSnapshot(@PathVariable("instanceId") Long instanceId,
                                                   @RequestBody(required = false) ReparseRequest request) {
        permissionService.requireInitiator("固化审批人快照");
        return ApiResponse.success(instanceService.reparse(instanceId, request));
    }

    /** 驳回重提：按最新已发布版本重新解析审批人快照（templates.md V-06）。 */
    @PostMapping("/{instanceId}/approver-snapshot/reparse")
    @Audited(action = "reparse_snapshot", targetType = "instance", targetId = "#instanceId", recordArgs = true)
    public ApiResponse<ReparseView> reparse(@PathVariable("instanceId") Long instanceId,
                                            @RequestBody(required = false) ReparseRequest request) {
        permissionService.requireInitiator("重新解析审批人快照");
        return ApiResponse.success(instanceService.reparse(instanceId, request));
    }
}
