package com.oa.form.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormDataService;
import com.oa.form.contract.ContractFormRules;
import com.oa.form.document.FormRuleContext;
import com.oa.form.document.FormRuleRegistry;
import com.oa.form.fund.FundFormRules;
import com.oa.form.matter.MatterFormRules;
import com.oa.form.seal.SealFormRules;
import com.oa.form.template.schema.FormFieldDef;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.form.template.validate.ValidationMode;
import com.oa.form.api.dto.FormDtos.CategoryChangeRequest;
import com.oa.form.api.dto.FormDtos.InvolveCostRequest;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import java.time.LocalDate;
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
 * 四类单据**专属规则**的判定接口（2b.3）—— 只读判定，不写库。
 *
 * <h2>路由清单（全部在 {@code /api/v1/forms} 之下）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>规则与取证</th></tr>
 *   <tr><td>POST</td><td>{@code /matter/fields/involve-cost/evaluate}</td>
 *       <td>事项单唯一分支：{@code involve_cost} → ②是否跳过（doc/forms.md §2 / prd §6.1）</td></tr>
 *   <tr><td>POST</td><td>{@code /matter/fields/category/lock}</td>
 *       <td>类别发起后不可改判（doc/forms.md §2 / prd §6.1 / TC-FORM-003）</td></tr>
 *   <tr><td>POST</td><td>{@code /fund/fields/plan-category/assert-storage-only}</td>
 *       <td>计划类别/付款归属「只存不用」，不得被 skip_condition 引用（doc/forms.md §3 末）</td></tr>
 *   <tr><td>POST</td><td>{@code /seal/fields/seal-type/linkage}</td>
 *       <td>证照借用 ↔ 证照类型/用印份数互斥（doc/forms.md §5 / dict-seed §3）</td></tr>
 *   <tr><td>GET</td><td>{@code /{formType}/field-groups}</td>
 *       <td>字段分组（{@code sections[]}，驱动界面分区与打印分组标题）</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/forms")
public class FormRuleController {

    private final FormSchemaService schemaService;
    private final FormRuleRegistry ruleRegistry;
    private final FormDataService formDataService;
    private final WorkflowPermissionService permissionService;
    /** 金额只读策略的**唯一**判定入口（与写路径 {@code FormStateWriteGuard} 同一实现）。 */
    private final com.oa.authz.visibility.FormFieldWriteGuard fieldWriteGuard;

    public FormRuleController(FormSchemaService schemaService, FormRuleRegistry ruleRegistry,
                              FormDataService formDataService, WorkflowPermissionService permissionService,
                              com.oa.authz.visibility.FormFieldWriteGuard fieldWriteGuard) {
        this.schemaService = schemaService;
        this.ruleRegistry = ruleRegistry;
        this.formDataService = formDataService;
        this.permissionService = permissionService;
        this.fieldWriteGuard = fieldWriteGuard;
    }

    // ================================================================ 事项单

    /** 事项单唯一分支：{@code involve_cost} 决定②财务部复核是否跳过。 */
    @PostMapping("/matter/fields/involve-cost/evaluate")
    public ApiResponse<Map<String, Object>> evaluateInvolveCost(@RequestBody(required = false) InvolveCostRequest request) {
        permissionService.requirePermission("事项单分支判定", FlowConfigPermission.FLOW_USE);
        Map<String, Object> values = new LinkedHashMap<>();
        if (request != null && request.fields() != null) {
            values.putAll(request.fields());
        }
        if (request != null && request.involveCost() != null) {
            values.put(MatterFormRules.FIELD_INVOLVE_COST, request.involveCost());
        }
        Map<String, Object> view = MatterFormRules.branchOf(values);
        view.put("engineAgreement", MatterFormRules.shouldSkipFinanceReview(values)
                == Boolean.TRUE.equals(view.get("skipFinanceReview")));
        view.put("categoryLock", categoryLockView(null, null, false));
        return ApiResponse.success(view);
    }

    /** 事项单类别改判判定（发起后不可改判）。 */
    @PostMapping("/matter/fields/category/lock")
    public ApiResponse<Map<String, Object>> categoryLock(@RequestBody(required = false) CategoryChangeRequest request) {
        permissionService.requirePermission("事项单类别改判判定", FlowConfigPermission.FLOW_USE);
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        boolean locked = request.state() != null && !request.state().isBlank()
                && !"draft".equalsIgnoreCase(request.state().trim());
        boolean changed = request.currentCategory() != null && request.newCategory() != null
                && !request.currentCategory().equals(request.newCategory());
        Map<String, Object> view = categoryLockView(request.currentCategory(), request.newCategory(), locked && changed);
        view.put("state", request.state());
        view.put("instanceId", request.instanceId());
        view.put("evaluatedBy", "纯判定接口（不写库）；写路径上的同口径判定见 FormDataService#save → MatterFormRules#validateCategoryLock");
        return ApiResponse.success(view);
    }

    private static Map<String, Object> categoryLockView(String current, String next, boolean denied) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("field", MatterFormRules.FIELD_CATEGORY);
        view.put("currentCategory", current);
        view.put("newCategory", next);
        view.put("immutableAfterSubmit", true);
        view.put("allowed", !denied);
        view.put("errorCode", denied ? ErrorCode.CATEGORY_IMMUTABLE.getCode() : null);
        view.put("remedy", "分类错误的唯一处理路径是「驳回 → 发起人修改 → 重新提交」");
        view.put("evidence", "doc/forms.md §2 业务补充说明第 1 条 / doc/prd-0.1.md §6.1 / "
                + "doc/enums.md §10.1 约束 1 / doc/test-cases.md TC-FORM-003");
        return view;
    }

    // ================================================================ 资金单

    /** 计划类别 / 付款归属「只存不用」的拒绝式机检。 */
    @PostMapping("/fund/fields/plan-category/assert-storage-only")
    public ApiResponse<Map<String, Object>> assertStorageOnly(@RequestBody(required = false) List<String> skipConditions) {
        permissionService.requirePermission("资金单只存不用机检", FlowConfigPermission.FLOW_USE);
        FundFormRules.assertNotRouted(skipConditions);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("fields", FundFormRules.STORAGE_ONLY_FIELDS);
        view.put("checkedConditions", skipConditions == null ? 0 : skipConditions.size());
        view.put("passed", true);
        view.put("evidence", "doc/forms.md §3 字段表与末段 / doc/templates.md §1.2 末 / doc/data-model.md §4.4");
        view.put("defaults", FundFormRules.defaults());
        return ApiResponse.success(view);
    }

    // ================================================================ 印鉴单

    /** 证照借用 ↔ 证照类型/用印份数互斥判定。 */
    @PostMapping("/seal/fields/seal-type/linkage")
    public ApiResponse<Map<String, Object>> sealLinkage(@RequestBody(required = false) Map<String, Object> fields) {
        permissionService.requirePermission("印鉴单联动判定", FlowConfigPermission.FLOW_USE);
        FormSchema schema = schemaService.publishedFor("seal");
        Map<String, Object> values = fields == null ? Map.of() : fields;
        FormRuleContext context = new FormRuleContext(schema, values, values.keySet(), Map.of(),
                ValidationMode.DRAFT, null, LocalDate.now());
        Map<String, Object> view = new LinkedHashMap<>(ruleRegistry.require("seal").describe(context));
        view.put("returnClosure", SealFormRules.threeStateException());
        return ApiResponse.success(view);
    }

    // ================================================================ 字段分组

    /** 字段分组（{@code sections[]}）。 */
    @GetMapping("/{formType}/field-groups")
    public ApiResponse<Map<String, Object>> fieldGroups(@PathVariable("formType") String formType) {
        permissionService.requirePermission("查看表单字段分组", FlowConfigPermission.FLOW_USE);
        FormSchema schema = schemaService.publishedFor(formType);
        Map<String, Object> view = new LinkedHashMap<>();
        List<Map<String, Object>> sections = new ArrayList<>();
        for (FormSchema.Section section : schema.sections()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", section.id());
            item.put("title", section.title());
            item.put("printTitle", section.printTitle());
            item.put("collapsible", section.collapsible());
            List<Map<String, Object>> fields = new ArrayList<>();
            for (String code : section.fields()) {
                schema.field(code).ifPresent(field -> fields.add(fieldView(field)));
            }
            item.put("fields", fields);
            sections.add(item);
        }
        view.put("formType", schema.formType());
        view.put("schemaVersion", schema.schemaVersion());
        view.put("sections", sections);
        // 金额字段对**当前登录角色**的读写策略。
        // 2026-10-04 缺陷修复：改前这里是 `FormDataService.amountPolicyOf(null)` —— 硬编码
        // null 主体使 `AmountFieldPolicy#canWriteAmounts(null)` 恒为 false，于是**所有账号
        // （含系统管理员与财务角色）都被前端告知「金额只读」**，而写路径（FormStateWriteGuard
        // → AmountFieldPolicy，按真实 principal 判）却放行 —— 「读说不能写、写却能写」。
        // 现在与写路径共用**同一个**实现（FormFieldWriteGuard#amountPolicy）与**同一个**主体来源
        // （DataScopeContext 的当前登录人），两条路径不可能再分叉。
        view.put("amountPolicy", fieldWriteGuard.amountPolicy(requirePrincipal()));
        return ApiResponse.success(view);
    }

    /**
     * 当前登录主体（与 {@code FormFieldWriteGuard#requirePrincipal} / {@code FieldPolicyController}
     * **同一来源**：{@code DataScopeContext} 里鉴权阶段装载的 principal）。
     *
     * <p>刻意不另造一份读取方式：面向用户的策略展示必须用「当前登录人」，
     * 而 DataScopeContext 是 AuthInterceptor 装载、AuthInterceptor 清理的单一事实源。
     */
    private static CurrentUser requirePrincipal() {
        com.oa.common.scope.DataScopeContext context = com.oa.common.scope.DataScopeContext.current();
        if (context == null || context.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return context.getPrincipal();
    }

    private static Map<String, Object> fieldView(FormFieldDef field) {
        return field.view();
    }

    /** 合同单必传文本字段披露（对外契约；文档口径见 doc/forms.md §4）。 */
    @GetMapping("/contract/required-text-fields")
    public ApiResponse<Map<String, Object>> contractRequiredTextFields() {
        permissionService.requirePermission("查看合同单必填文本字段", FlowConfigPermission.FLOW_USE);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("fields", ContractFormRules.requiredTextFields());
        view.put("blankRule", "空字符串与全空格视为未填（doc/forms.md §1.3 必填行）");
        view.put("creditCodePattern", ContractFormRules.CREDIT_CODE_PATTERN);
        return ApiResponse.success(view);
    }
}
