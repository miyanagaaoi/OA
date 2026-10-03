package com.oa.form.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.common.security.CurrentUser;
import com.oa.form.api.dto.FormDtos.FormWriteRequest;
import com.oa.form.api.dto.FormDtos.SealReturnRequest;
import com.oa.form.app.FormDataService;
import com.oa.form.template.validate.ValidationMode;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单数据接口（2b.1 / 2b.2 / 2b.3）—— 服务端二次校验与三态白名单的**唯一对外写入口**。
 *
 * <h2>路由清单（全部在 {@code /api/v1} 之下）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>入口闸门</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>{@code /forms/{formType}/validate}</td><td>{@code flow}</td>
 *       <td>新建前干跑校验（用当前已发布 schema）</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/instances/{id}/schema}</td><td>{@code flow} ∪ {@code admin:flow}</td>
 *       <td><b>实例锁定版本</b>的 schema（AC-09：不读当前 published）</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/instances/{id}/draft}</td><td>{@code flow} ∪ {@code admin:flow}</td>
 *       <td>读取单据（字段值 + 名称快照 + 可写字段 + 专属派生信息）</td></tr>
 *   <tr><td>PUT</td><td>{@code /forms/instances/{id}/draft}</td><td>{@code flow}</td>
 *       <td>保存（三态白名单 + schema 校验 + 专属规则 + 快照固化）</td></tr>
 *   <tr><td>POST</td><td>{@code /forms/instances/{id}/validate}</td><td>{@code flow}</td>
 *       <td>提交前校验（干跑，**返回全部失败项**，不改库）</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/instances/{id}/writable-fields}</td><td>{@code flow} ∪ {@code admin:flow}</td>
 *       <td>当前状态可写字段（前端置灰提示；边界在服务端）</td></tr>
 *   <tr><td>PUT</td><td>{@code /forms/seal/instances/{id}/return-status}</td><td>{@code flow}</td>
 *       <td>印鉴单归还登记（三态**唯一例外**：审批中仅发起人与节点⑦可改）</td></tr>
 * </table>
 *
 * <p><b>入口闸门的取值口径与 {@code FlowInstanceController} 一致</b>：
 * 写入口取「该动作自己的权限码」{@code flow}（{@link FlowConfigPermission#FLOW_USE}），
 * 只读入口取 {@code requireInitiator}（{@code flow} ∪ {@code admin:flow}）——
 * 避免「入口比动作面宽」。
 *
 * <p>数据域由 Mapper 层织入（{@code form_data} / {@code flow_instance} 都是受控表），
 * 域外实例在此读不到，按 404 返回。
 */
@RestController
@RequestMapping("/api/v1/forms")
public class FormDataController {

    private final FormDataService formDataService;
    private final WorkflowPermissionService permissionService;

    public FormDataController(FormDataService formDataService, WorkflowPermissionService permissionService) {
        this.formDataService = formDataService;
        this.permissionService = permissionService;
    }

    // ================================================================ 干跑校验（无实例）

    /** 新建前的按单据类型干跑校验（不落库）。 */
    @PostMapping("/{formType}/validate")
    public ApiResponse<Map<String, Object>> validateByFormType(@PathVariable("formType") String formType,
                                                               @RequestParam(name = "mode", required = false)
                                                               String mode,
                                                               @RequestBody(required = false) FormWriteRequest request) {
        permissionService.requirePermission("表单校验", FlowConfigPermission.FLOW_USE);
        return ApiResponse.success(formDataService.validateByFormType(formType,
                request == null ? Map.of() : request.fields(), ValidationMode.of(mode)));
    }

    // ================================================================ 实例：schema / 读

    /** 实例锁定版本的 schema（AC-09）。 */
    @GetMapping("/instances/{instanceId}/schema")
    public ApiResponse<Map<String, Object>> schemaOfInstance(@PathVariable("instanceId") Long instanceId) {
        permissionService.requireInitiator("查看单据表单模板");
        return ApiResponse.success(formDataService.schemaOfInstance(instanceId));
    }

    /** 读取单据（字段值 + 快照版本 + 可写字段 + 专属派生信息）。 */
    @GetMapping("/instances/{instanceId}/draft")
    public ApiResponse<Map<String, Object>> read(@PathVariable("instanceId") Long instanceId) {
        permissionService.requireInitiator("查看单据表单");
        return ApiResponse.success(formDataService.read(instanceId));
    }

    /** 当前状态的可写字段（前端置灰提示）。 */
    @GetMapping("/instances/{instanceId}/writable-fields")
    public ApiResponse<Map<String, Object>> writableFields(@PathVariable("instanceId") Long instanceId) {
        permissionService.requireInitiator("查看可写字段");
        return ApiResponse.success(formDataService.writableFields(instanceId));
    }

    // ================================================================ 实例：写

    /** 保存草稿 / 补件（三态白名单 + schema 校验 + 专属规则 + 快照固化）。 */
    @PutMapping("/instances/{instanceId}/draft")
    @Audited(action = "form_save", targetType = "form_data", targetId = "#instanceId", recordArgs = true)
    public ApiResponse<Map<String, Object>> save(@PathVariable("instanceId") Long instanceId,
                                                 @RequestParam(name = "mode", required = false) String mode,
                                                 @RequestBody(required = false) FormWriteRequest request) {
        permissionService.requirePermission("保存单据表单", FlowConfigPermission.FLOW_USE);
        return ApiResponse.success(formDataService.save(instanceId,
                request == null ? Map.of() : request.fields(), ValidationMode.of(mode)));
    }

    /** 提交前校验（干跑，返回**全部**失败项）。 */
    @PostMapping("/instances/{instanceId}/validate")
    public ApiResponse<Map<String, Object>> validateInstance(@PathVariable("instanceId") Long instanceId,
                                                             @RequestParam(name = "mode", required = false)
                                                             String mode,
                                                             @RequestBody(required = false)
                                                             FormWriteRequest request) {
        permissionService.requirePermission("表单校验", FlowConfigPermission.FLOW_USE);
        return ApiResponse.success(formDataService.validateInstance(instanceId,
                request == null ? Map.of() : request.fields(), ValidationMode.of(mode)));
    }

    /** 印鉴单归还登记（三态唯一例外的专用通道；只接受 {@code return_status}/{@code return_date}）。 */
    @PutMapping("/seal/instances/{instanceId}/return-status")
    @Audited(action = "seal_return_register", targetType = "form_data", targetId = "#instanceId", recordArgs = true)
    public ApiResponse<Map<String, Object>> registerSealReturn(@PathVariable("instanceId") Long instanceId,
                                                               @RequestBody SealReturnRequest request) {
        CurrentUser principal = permissionService.requirePermission("归还登记", FlowConfigPermission.FLOW_USE);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request != null) {
            if (request.returnStatus() != null) {
                payload.put("return_status", request.returnStatus());
            }
            if (request.returnDate() != null) {
                payload.put("return_date", request.returnDate());
            }
        }
        return ApiResponse.success(formDataService.registerSealReturn(instanceId, payload));
    }
}
