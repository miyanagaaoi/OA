package com.oa.form.api;

import com.oa.common.api.ApiResponse;
import com.oa.form.dict.DictType;
import com.oa.form.dict.FormDictService;
import com.oa.form.api.dto.FormDtos.DictItemView;
import com.oa.form.api.dto.FormDtos.DictTypeView;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据字典与表单模板 schema 接口（2b.4 / 2b.1）。
 *
 * <h2>路由清单（全部在 {@code /api/v1} 之下）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>{@code /forms/dicts}</td>
 *       <td>字典类型白名单（8 类）+ 行数（自检口径见 doc/dict-seed.md §11）</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/dicts/{dictType}/items}</td>
 *       <td>下拉取值（**启用项**，按 sort_no 排序；类别为配置项，新增无需发版）</td></tr>
 *   <tr><td>POST</td><td>{@code /forms/dicts/cache/refresh}</td>
 *       <td>后台改字典后显式失效启用项缓存</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/templates}</td><td>四类单据的已发布模板与 schema 版本</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/templates/{formType}/schema}</td>
 *       <td>取某模板版本的表单 schema（{@code ?version=} 缺省取当前已发布）</td></tr>
 * </table>
 *
 * <p>权限统一为 {@code flow}：字典与 schema 是**渲染与校验的公共输入**，
 * 任何能发起单据的主体都应能读到（配置面的维护入口属阶段 4 的 {@code oa.admin.dict.*}）。
 */
@RestController
@RequestMapping("/api/v1/forms")
public class FormTemplateController {

    private final FormDictService dictService;
    private final FormSchemaService schemaService;
    private final WorkflowPermissionService permissionService;

    public FormTemplateController(FormDictService dictService, FormSchemaService schemaService,
                                  WorkflowPermissionService permissionService) {
        this.dictService = dictService;
        this.schemaService = schemaService;
        this.permissionService = permissionService;
    }

    /** 字典类型白名单 + 行数。 */
    @GetMapping("/dicts")
    public ApiResponse<List<DictTypeView>> dicts() {
        permissionService.requirePermission("查看数据字典", FlowConfigPermission.FLOW_USE);
        List<DictTypeView> views = new ArrayList<>();
        for (DictType type : DictType.values()) {
            views.add(new DictTypeView(type.code(), type.label(),
                    dictService.allItems(type.code()).size(),
                    "doc/dict-seed.md §0.3 字典类型白名单（8 类）"));
        }
        return ApiResponse.success(views);
    }

    /** 下拉取值（启用项）。 */
    @GetMapping("/dicts/{dictType}/items")
    public ApiResponse<List<DictItemView>> dictItems(@PathVariable("dictType") String dictType) {
        permissionService.requirePermission("查看数据字典", FlowConfigPermission.FLOW_USE);
        List<DictItemView> views = new ArrayList<>();
        dictService.options(dictType).forEach(item -> views.add(new DictItemView(item.dictType(),
                item.itemCode(), item.itemName(), item.itemNameEn(), item.sortNo(), item.status())));
        return ApiResponse.success(views);
    }

    /** 失效启用项缓存（后台改字典后调用）。 */
    @PostMapping("/dicts/cache/refresh")
    public ApiResponse<Map<String, Object>> refreshDictCache() {
        permissionService.requirePermission("刷新数据字典缓存", FlowConfigPermission.FLOW_USE);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("clearedTypes", dictService.refresh());
        view.put("counts", dictService.counts());
        return ApiResponse.success(view);
    }

    /** 四类单据的模板清单（含已发布 schema 版本）。 */
    @GetMapping("/templates")
    public ApiResponse<List<Map<String, Object>>> templates() {
        permissionService.requirePermission("查看表单模板", FlowConfigPermission.FLOW_USE);
        List<Map<String, Object>> views = new ArrayList<>();
        for (String formType : List.of("matter", "fund", "contract", "seal")) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("formType", formType);
            try {
                var schema = schemaService.publishedFor(formType);
                view.put("templateCode", schema.templateCode());
                view.put("schemaVersion", schema.schemaVersion());
                view.put("fieldCount", schema.fields().size());
                view.put("published", true);
            } catch (RuntimeException ex) {
                view.put("published", false);
                view.put("message", ex.getMessage());
            }
            views.add(view);
        }
        return ApiResponse.success(views);
    }

    /**
     * 取某模板版本的表单 schema。
     *
     * @param version 缺省 = 该单据类型当前已发布版本；给定版本则按 {@code (code, version)} 精确取
     *                （doc/templates.md §3.2 V-01：版本按 {@code (code, version)} 唯一累积）
     */
    @GetMapping("/templates/{formType}/schema")
    public ApiResponse<Map<String, Object>> schema(@PathVariable("formType") String formType,
                                                   @RequestParam(name = "version", required = false)
                                                   Integer version) {
        permissionService.requirePermission("查看表单模板", FlowConfigPermission.FLOW_USE);
        if (version == null) {
            return ApiResponse.success(schemaService.publishedFor(formType).view());
        }
        return ApiResponse.success(schemaService.forCodeAndVersion(formType, version).view());
    }
}
