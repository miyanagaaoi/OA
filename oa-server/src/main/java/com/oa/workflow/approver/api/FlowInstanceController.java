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
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.domain.FlowAction;
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
 * <h2>路由清单（全部在 {@code /api/v1} 之下）</h2>
 *
 * <p><b>入口闸门的两档口径（2026-10-04 收敛）</b>：<b>建草稿</b>（写）取「发起」这一动作自己的权限码
 * {@code flow}；其余入口（预检 / 列表 / 详情 / 快照，含只读与干跑）沿用 {@code requireInitiator}
 * = {@code flow} ∪ {@code admin:flow}。
 * <ul>
 *   <li><b>为什么建草稿收紧到 {@code flow}</b>：A–G 已把 {@code submit} / {@code reopen} /
 *       {@code resubmit} / {@code supplement} 收敛到 {@link FlowAction} 的动作码，建草稿若仍放行
 *       {@code admin:flow}，入口就比动作面宽（只持 {@code admin:flow} 的自定义角色能建草稿、
 *       却在 {@code submit} 处被拒）；</li>
 *   <li><b>为什么不会收窄业务可用面</b>：{@code doc/prd-0.1.md} 附录A「发起审批」行 8 个角色全为 ✓，
 *       而 {@code flow} 是门户基础权限、9 个内置角色**全部持有**（{@code company_admin} 亦已在
 *       V4 种子中补齐 {@code flow}）；因此本改动只影响「只持 {@code admin:flow} 的自定义角色」这一
 *       非产品口径的用法 —— 也正是「入口比引擎宽」的那一类；</li>
 *   <li>只读入口刻意保持较宽：列表 / 详情 / 快照对 {@code admin:flow} 主体本就应当可读
 *       （数据域已在查询层过滤），不属本轮收敛范围。</li>
 * </ul>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>入口闸门</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/precheck}</td><td>{@code flow} ∪ {@code admin:flow}</td><td><b>发起前预检</b>（只读干跑）：逐节点解析候选人，任一为空即 {@code allowed=false} 并给出节点/规则/缺配</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/precheck/rules}</td><td>{@code flow} ∪ {@code admin:flow}</td><td>预检规则清单（已实现项与后续工作包负责项都显式列出）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances}</td><td><b>{@code flow}</b>（= 发起动作码，2026-10-04 收紧）</td><td>建草稿实例：预检通过 → <b>锁定模板版本</b> → 固化审批人快照</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{instanceId}/submit}</td><td>{@code FlowAction.SUBMIT.permission()} = {@code flow}</td><td>提交（{@code draft → approving}）；与引擎同源，2a.4 收敛</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances}</td><td>{@code flow} ∪ {@code admin:flow}</td><td>实例列表（按模板 / 版本 / 状态 / 发起人过滤；数据域过滤）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{instanceId}}</td><td>{@code flow} ∪ {@code admin:flow}</td><td>实例详情（含快照）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-instances/{instanceId}/approver-snapshot}</td><td>{@code flow} ∪ {@code admin:flow}</td><td>读取作为运行时权威数据的快照（数据域过滤）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{instanceId}/approver-snapshot}</td><td>{@code flow} ∪ {@code admin:flow}</td><td>（重新）解析并固化快照（口径同 reparse）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-instances/{instanceId}/approver-snapshot/reparse}</td><td>{@code flow} ∪ {@code admin:flow}</td><td>驳回重提时按最新已发布版本重新解析，旧快照写审计日志</td></tr>
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

    /**
     * 建草稿实例（预检通过才会落库；同时锁定模板版本并固化快照）。
     *
     * <p><b>入口闸门 = {@code flow}（门户基础权限，2026-10-04 由 {@code requireInitiator} 收紧）</b>：
     * 「发起」这一动作的口径就是 {@code flow} —— 与 {@code submit} 收敛后的入口同源，
     * 入口不再放行「只持 {@code admin:flow}」的主体（那类主体在本入口放行、随后会在
     * {@code /submit} 被引擎按 {@code flow} 拒，属「入口比动作面宽」）。
     * 依据：{@code doc/prd-0.1.md} 附录A「发起审批」行 8 个角色全 ✓，且 V4 种子已给
     * {@code company_admin} 补上 {@code flow}，故业务可用面不变。
     *
     * <p>权限判定与「发起人身份」判定分清：本接口不接受 {@code initiatorId}（发起人恒为当前登录人），
     * 由 {@code FlowInstanceService#create} 按登录人落快照，因此不需要第二层身份闸门。
     */
    @PostMapping
    @Audited(action = "create", targetType = "instance", recordArgs = true, recordAfter = false)
    public ApiResponse<InstanceView> create(@RequestBody CreateInstanceRequest request) {
        CurrentUser principal = permissionService.requirePermission("发起审批单", FlowConfigPermission.FLOW_USE);
        return ApiResponse.success(instanceService.create(request, principal));
    }

    /**
     * 提交（草稿 → 审批中；建节点实例 + 首个节点待办由运行时引擎完成）。
     *
     * <p><b>入口闸门 = {@code FlowAction.SUBMIT.permission()}（{@code flow}）</b>（F 项收敛，2026-10-04）：
     * 引擎 {@code FlowEngineService#submit} 取的是同一个动作码，因此本入口与引擎**同源同参**
     * —— 改前这里用 {@code requireInitiator}（{@code flow} ∪ {@code admin:flow}），
     * 比引擎宽：只持 {@code admin:flow} 的自定义角色能过入口、随后被引擎 403。
     * <p>发起人身份（{@code requireInitiatorOrAdmin}）仍由引擎判，入口不读实例
     * （口径与 {@code /reopen} / {@code /resubmit} / {@code /supplement} 一致）。
     *
     * <p><b>建草稿（{@code POST /flow-instances}）此前是刻意保留的最后一道宽口</b>，
     * 已于 2026-10-04 一并收紧到 {@code flow}（见 {@link #create} 的注释）：
     * 「发起」与动作面里的 {@code submit} 同口径，入口不再放行只持 {@code admin:flow} 的主体。
     */
    @PostMapping("/{instanceId}/submit")
    @Audited(action = "submit", targetType = "instance", targetId = "#instanceId", recordArgs = true)
    public ApiResponse<InstanceView> submit(@PathVariable("instanceId") Long instanceId,
                                            @Valid @RequestBody SubmitRequest request) {
        permissionService.requirePermission(FlowAction.SUBMIT.label(), FlowAction.SUBMIT.permission());
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
