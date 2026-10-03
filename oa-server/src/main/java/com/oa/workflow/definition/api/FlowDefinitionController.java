package com.oa.workflow.definition.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.CheckRuleView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.DecisionResolveView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.GatePolicyRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NewVersionRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeApproverRuleRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeDecisionRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeOrderRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodePolicyRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeSkipConditionRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.PrePublishReportView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.PublishRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.TemplateDetailView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.TemplateUpdateRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.TemplateView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.ValidationView;
import com.oa.workflow.definition.app.FlowDefinitionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程模板与节点定义接口（2a.2，{@code oa.workflow.definition.*}）。
 *
 * <h2>路由清单（全部在 {@code /api/v1} 之下）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>所需权限</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>{@code /flow-templates}</td><td>admin:flow:template</td><td>模板列表（按 code/formType/status 过滤）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-templates/{templateId}}</td><td>admin:flow:template</td><td>模板详情（含节点）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-templates/{templateId}/nodes}</td><td>admin:flow:template</td><td>节点清单（按 seq）</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-templates/{templateId}/versions}</td><td>admin:flow:template</td><td>版本历史</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-templates/{templateId}/versions/{version}}</td><td>admin:flow:template</td><td><b>按版本查询</b>（含该版本节点）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-templates/{templateId}/versions}</td><td>admin:flow:publish</td><td>基于已发布/已归档版本开新草稿（version+1）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-templates/{templateId}/publish}</td><td>admin:flow:publish</td><td>发布（先跑发布前校验；原 published 转 archived）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-templates/{templateId}/archive}</td><td>admin:flow:publish</td><td>归档（只阻止新实例，在途继续）</td></tr>
 *   <tr><td>PUT</td><td>{@code /flow-templates/{templateId}}</td><td>admin:flow:publish</td><td>草稿元数据（名称 / 表单定义 / 闸门配置）</td></tr>
 *   <tr><td>PUT</td><td>{@code /flow-templates/{templateId}/gate-policy}</td><td>admin:flow:publish</td><td><b>Q6/Q7 闸门配置</b>写入</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-templates/{templateId}/nodes}</td><td>admin:flow:publish</td><td>新增节点（可指定插入位置）</td></tr>
 *   <tr><td>PUT</td><td>{@code /flow-templates/{templateId}/nodes/order}</td><td>admin:flow:publish</td><td>节点换序（全排列）</td></tr>
 *   <tr><td>PUT</td><td>{@code /flow-nodes/{nodeId}}</td><td>admin:flow:publish</td><td>节点全量更新（决议/阈值/签名/超时/开关/跳过条件/解析规则）</td></tr>
 *   <tr><td>DELETE</td><td>{@code /flow-nodes/{nodeId}}</td><td>admin:flow:publish</td><td>删除节点（<b>主干必填节点拒绝</b>）</td></tr>
 *   <tr><td>GET/PUT</td><td>{@code /flow-nodes/{nodeId}/decision}</td><td>读 template / 写 publish</td><td>决议模式与阈值</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-nodes/{nodeId}/decision/resolve}</td><td>admin:flow:template</td><td>按候选人集合解析通过条件（纯计算）</td></tr>
 *   <tr><td>GET/PUT</td><td>{@code /flow-nodes/{nodeId}/policy}</td><td>读 template / 写 publish</td><td>签名策略 / 超时 / 加签 / 跳转 / 流转开关</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-nodes/{nodeId}/policy/validate}</td><td>admin:flow:template</td><td>策略校验（不落库）</td></tr>
 *   <tr><td>GET/PUT</td><td>{@code /flow-nodes/{nodeId}/skip-condition}</td><td>读 template / 写 publish</td><td>跳过条件</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-nodes/{nodeId}/skip-condition/validate}</td><td>admin:flow:template</td><td>跳过条件校验（字段存在性 + 操作符白名单，不落库）</td></tr>
 *   <tr><td>GET/PUT</td><td>{@code /flow-nodes/{nodeId}/approver-rule}</td><td>读 template / 写 publish</td><td>审批人解析规则与参数</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-nodes/{nodeId}/approver-rule/validate}</td><td>admin:flow:template</td><td>规则校验（不落库）</td></tr>
 *   <tr><td>POST</td><td>{@code /flow-designs/{templateId}/pre-publish-check}</td><td>admin:flow:template</td><td><b>发布前 dry-run 校验报告</b></td></tr>
 *   <tr><td>GET</td><td>{@code /flow-designs/{templateId}/pre-publish-check/latest}</td><td>admin:flow:template</td><td>最近一次校验结果</td></tr>
 *   <tr><td>GET</td><td>{@code /flow-designs/check-rules}</td><td>admin:flow:template</td><td>校验规则清单</td></tr>
 * </table>
 *
 * <p>权限判定在服务层（{@code WorkflowPermissionService} → {@code FlowConfigPermission}），
 * 控制器只做入参绑定；模板/节点是**配置数据**，不织入数据域（见 {@code FlowTemplateMapper} 的类注释）。
 */
@RestController
@RequestMapping("/api/v1")
public class FlowDefinitionController {

    private final FlowDefinitionService service;

    public FlowDefinitionController(FlowDefinitionService service) {
        this.service = service;
    }

    // ================================================================ 模板查询

    @GetMapping("/flow-templates")
    public ApiResponse<List<TemplateView>> list(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "formType", required = false) String formType,
            @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.success(service.list(code, formType, status));
    }

    @GetMapping("/flow-templates/{templateId}")
    public ApiResponse<TemplateDetailView> detail(@PathVariable("templateId") Long templateId) {
        return ApiResponse.success(service.detail(templateId));
    }

    @GetMapping("/flow-templates/{templateId}/nodes")
    public ApiResponse<List<NodeView>> nodes(@PathVariable("templateId") Long templateId) {
        return ApiResponse.success(service.nodes(templateId));
    }

    @GetMapping("/flow-templates/{templateId}/versions")
    public ApiResponse<List<TemplateView>> versions(@PathVariable("templateId") Long templateId) {
        return ApiResponse.success(service.versions(templateId));
    }

    @GetMapping("/flow-templates/{templateId}/versions/{version}")
    public ApiResponse<TemplateDetailView> byVersion(@PathVariable("templateId") Long templateId,
                                                     @PathVariable("version") Integer version) {
        return ApiResponse.success(service.detailByVersion(templateId, version));
    }

    // ================================================================ 版本管理

    @PostMapping("/flow-templates/{templateId}/versions")
    @Audited(action = "create", targetType = "flow_template", targetId = "#templateId", recordArgs = true,
            recordAfter = false)
    public ApiResponse<TemplateView> newVersion(@PathVariable("templateId") Long templateId,
                                                @RequestBody(required = false) NewVersionRequest request) {
        return ApiResponse.success(service.newVersion(templateId, request));
    }

    @PostMapping("/flow-templates/{templateId}/publish")
    @Audited(action = "publish_template", targetType = "flow_template", targetId = "#templateId",
            recordBefore = true, recordArgs = true)
    public ApiResponse<TemplateView> publish(@PathVariable("templateId") Long templateId,
                                             @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(service.publish(templateId, request));
    }

    @PostMapping("/flow-templates/{templateId}/archive")
    @Audited(action = "archive_template", targetType = "flow_template", targetId = "#templateId",
            recordBefore = true, recordArgs = true)
    public ApiResponse<TemplateView> archive(@PathVariable("templateId") Long templateId,
                                             @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(service.archive(templateId, request));
    }

    @PutMapping("/flow-templates/{templateId}")
    @Audited(action = "update", targetType = "flow_template", targetId = "#templateId", recordBefore = true)
    public ApiResponse<TemplateView> updateTemplate(@PathVariable("templateId") Long templateId,
                                                    @RequestBody TemplateUpdateRequest request) {
        return ApiResponse.success(service.updateTemplate(templateId, request));
    }

    /** Q6/Q7 闸门配置写入（2026-10-03 产品裁定：两者均为可配置项，键位见 templates.md §1.7）。 */
    @PutMapping("/flow-templates/{templateId}/gate-policy")
    @Audited(action = "update", targetType = "flow_template", targetId = "#templateId", recordBefore = true,
            recordArgs = true)
    public ApiResponse<TemplateView> putGatePolicy(@PathVariable("templateId") Long templateId,
                                                   @Valid @RequestBody GatePolicyRequest request) {
        return ApiResponse.success(service.putGatePolicy(templateId, request));
    }

    // ================================================================ 节点增删改

    @PostMapping("/flow-templates/{templateId}/nodes")
    @Audited(action = "create", targetType = "flow_node", recordArgs = true)
    public ApiResponse<NodeView> addNode(@PathVariable("templateId") Long templateId,
                                         @Valid @RequestBody NodeRequest request) {
        return ApiResponse.success(service.addNode(templateId, request));
    }

    @PutMapping("/flow-templates/{templateId}/nodes/order")
    @Audited(action = "update", targetType = "flow_node", targetId = "#templateId", recordArgs = true)
    public ApiResponse<List<NodeView>> reorder(@PathVariable("templateId") Long templateId,
                                               @RequestBody NodeOrderRequest request) {
        return ApiResponse.success(service.reorder(templateId, request));
    }

    @PutMapping("/flow-nodes/{nodeId}")
    @Audited(action = "update", targetType = "flow_node", targetId = "#nodeId", recordBefore = true)
    public ApiResponse<NodeView> updateNode(@PathVariable("nodeId") Long nodeId,
                                            @Valid @RequestBody NodeRequest request) {
        return ApiResponse.success(service.updateNode(nodeId, request));
    }

    @DeleteMapping("/flow-nodes/{nodeId}")
    @Audited(action = "delete", targetType = "flow_node", targetId = "#nodeId", recordBefore = true)
    public ApiResponse<Void> deleteNode(@PathVariable("nodeId") Long nodeId) {
        service.deleteNode(nodeId);
        return ApiResponse.success(null);
    }

    // ================================================================ 决议模式与阈值

    @GetMapping("/flow-nodes/{nodeId}/decision")
    public ApiResponse<NodeView> decision(@PathVariable("nodeId") Long nodeId) {
        return ApiResponse.success(service.node(nodeId));
    }

    @PutMapping("/flow-nodes/{nodeId}/decision")
    @Audited(action = "update", targetType = "flow_node", targetId = "#nodeId", recordBefore = true)
    public ApiResponse<NodeView> putDecision(@PathVariable("nodeId") Long nodeId,
                                             @RequestBody NodeDecisionRequest request) {
        return ApiResponse.success(service.putDecision(nodeId, request));
    }

    /** 按候选人集合解析通过条件（百分比向上取整、绝对人数优先、NULL = 过半）。 */
    @PostMapping("/flow-nodes/{nodeId}/decision/resolve")
    public ApiResponse<DecisionResolveView> resolveDecision(
            @PathVariable("nodeId") Long nodeId,
            @RequestParam(name = "candidateCount", required = false, defaultValue = "0") Integer candidateCount) {
        return ApiResponse.success(service.resolveDecision(nodeId, candidateCount));
    }

    // ================================================================ 签名策略 / 超时 / 开关

    @GetMapping("/flow-nodes/{nodeId}/policy")
    public ApiResponse<NodeView> policy(@PathVariable("nodeId") Long nodeId) {
        return ApiResponse.success(service.node(nodeId));
    }

    @PutMapping("/flow-nodes/{nodeId}/policy")
    @Audited(action = "update", targetType = "flow_node", targetId = "#nodeId", recordBefore = true)
    public ApiResponse<NodeView> putPolicy(@PathVariable("nodeId") Long nodeId,
                                           @RequestBody NodePolicyRequest request) {
        return ApiResponse.success(service.putPolicy(nodeId, request));
    }

    @PostMapping("/flow-nodes/{nodeId}/policy/validate")
    public ApiResponse<ValidationView> validatePolicy(@PathVariable("nodeId") Long nodeId,
                                                      @RequestBody NodePolicyRequest request) {
        return ApiResponse.success(service.validatePolicy(nodeId, request));
    }

    // ================================================================ 跳过条件

    @GetMapping("/flow-nodes/{nodeId}/skip-condition")
    public ApiResponse<NodeView> skipCondition(@PathVariable("nodeId") Long nodeId) {
        return ApiResponse.success(service.node(nodeId));
    }

    @PutMapping("/flow-nodes/{nodeId}/skip-condition")
    @Audited(action = "update", targetType = "flow_node", targetId = "#nodeId", recordBefore = true)
    public ApiResponse<NodeView> putSkipCondition(@PathVariable("nodeId") Long nodeId,
                                                  @RequestBody NodeSkipConditionRequest request) {
        return ApiResponse.success(service.putSkipCondition(nodeId, request));
    }

    @PostMapping("/flow-nodes/{nodeId}/skip-condition/validate")
    public ApiResponse<ValidationView> validateSkipCondition(@PathVariable("nodeId") Long nodeId,
                                                             @RequestBody NodeSkipConditionRequest request) {
        return ApiResponse.success(service.validateSkipCondition(nodeId, request));
    }

    // ================================================================ 审批人解析规则声明

    @GetMapping("/flow-nodes/{nodeId}/approver-rule")
    public ApiResponse<NodeView> approverRule(@PathVariable("nodeId") Long nodeId) {
        return ApiResponse.success(service.node(nodeId));
    }

    @PutMapping("/flow-nodes/{nodeId}/approver-rule")
    @Audited(action = "update", targetType = "flow_node", targetId = "#nodeId", recordBefore = true)
    public ApiResponse<NodeView> putApproverRule(@PathVariable("nodeId") Long nodeId,
                                                 @RequestBody NodeApproverRuleRequest request) {
        return ApiResponse.success(service.putApproverRule(nodeId, request));
    }

    @PostMapping("/flow-nodes/{nodeId}/approver-rule/validate")
    public ApiResponse<ValidationView> validateApproverRule(@PathVariable("nodeId") Long nodeId,
                                                            @RequestBody NodeApproverRuleRequest request) {
        return ApiResponse.success(service.validateApproverRule(nodeId, request));
    }

    // ================================================================ 发布前 dry-run

    @PostMapping("/flow-designs/{templateId}/pre-publish-check")
    public ApiResponse<PrePublishReportView> prePublishCheck(@PathVariable("templateId") Long templateId) {
        return ApiResponse.success(service.prePublishCheck(templateId));
    }

    @GetMapping("/flow-designs/{templateId}/pre-publish-check/latest")
    public ApiResponse<PrePublishReportView> latestPrePublishCheck(@PathVariable("templateId") Long templateId) {
        return ApiResponse.success(service.latestPrePublishCheck(templateId));
    }

    @GetMapping("/flow-designs/check-rules")
    public ApiResponse<List<CheckRuleView>> checkRules() {
        return ApiResponse.success(service.checkRules());
    }
}
