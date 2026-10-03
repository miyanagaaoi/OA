package com.oa.admin.bulk.api;

import com.oa.admin.bulk.BulkExportService;
import com.oa.admin.bulk.BulkImportService;
import com.oa.admin.bulk.ImportKind;
import com.oa.admin.bulk.ImportReport;
import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 组织人员批量维护（阶段 1.8）—— <b>五类导入 + 四类导出</b>的完整路由表。
 *
 * <h2>路由表（preview = 干跑校验报告；无后缀 = 事务落库）</h2>
 * <table>
 *   <tr><th>#</th><th>类别</th><th>模板</th><th>preview</th><th>commit</th><th>export</th></tr>
 *   <tr><td>①</td><td>组织架构</td><td>{@code org.csv}</td>
 *       <td>{@code POST /api/v1/identity/orgs/import/preview}</td>
 *       <td>{@code POST /api/v1/identity/orgs/import}</td>
 *       <td>{@code GET /api/v1/identity/orgs/export}（既有）</td></tr>
 *   <tr><td>②</td><td>人员</td><td>{@code user.csv}</td>
 *       <td>{@code POST /api/v1/identity/users/import/preview}</td>
 *       <td>{@code POST /api/v1/identity/users/import}</td>
 *       <td>{@code GET /api/v1/identity/users/export}（既有）</td></tr>
 *   <tr><td>③</td><td>组织负责人</td><td>{@code org_leader.csv}</td>
 *       <td>{@code POST /api/v1/identity/org-leaders/import/preview}</td>
 *       <td>{@code POST /api/v1/identity/org-leaders/import}</td>
 *       <td>{@code GET /api/v1/identity/org-leaders/export}</td></tr>
 *   <tr><td>④</td><td>岗位任职</td><td>{@code user_position.csv}</td>
 *       <td>{@code POST /api/v1/identity/user-positions/import/preview}</td>
 *       <td>{@code POST /api/v1/identity/user-positions/import}</td>
 *       <td>{@code GET /api/v1/identity/user-positions/export}</td></tr>
 *   <tr><td>⑤</td><td>角色分配</td><td>{@code user_role.csv}</td>
 *       <td>{@code POST /api/v1/identity/user-roles/import/preview}</td>
 *       <td>{@code POST /api/v1/identity/user-roles/import}</td>
 *       <td>{@code GET /api/v1/identity/user-roles/export}</td></tr>
 * </table>
 * 另有：
 * <ul>
 *   <li>{@code POST /api/v1/admin/bulk-import/impact-preview}：受影响在途单据清单（§7，dry-run）；</li>
 *   <li>{@code GET  /api/v1/admin/audit-logs/export}：审计日志导出（金额键剔除，§9.2 / PRD §5.3）。</li>
 * </ul>
 *
 * <h2>调用顺序（§2.1 不可调换）</h2>
 * 组织 → 人员 → 负责人 → 岗位 → 角色分配；后一步的前置条件由前一步落库满足
 * （人员/负责人/岗位/角色的组织与账号解析都查库）。
 *
 * <h2>入参形态</h2>
 * 两种都支持：{@code multipart/form-data} 的 {@code file} 字段（浏览器上传），
 * 或直接把 CSV 作为请求体（{@code curl --data-binary @org.csv}，便于脚本化与实测）。
 * 文件必须是 <b>UTF-8 BOM</b> 的 CSV（import-spec §4.1）。
 *
 * <h2>权限</h2>
 * 系统管理员或分公司流程管理员；分公司管理员逐行受数据域约束（跨域行 fail-closed 拒绝整批）。
 * 导入为高危写操作，全部走 {@code @Audited} 留痕；commit 的响应体含**仅本次返回**的初始口令，
 * 因此 {@code recordAfter=false}（口令绝不进审计日志）。
 */
@RestController
public class BulkImportController {

    private final BulkImportService importService;

    private final BulkExportService exportService;

    public BulkImportController(BulkImportService importService, BulkExportService exportService) {
        this.importService = importService;
        this.exportService = exportService;
    }

    // ------------------------------------------------------------------ ① 组织架构

    @PostMapping("/api/v1/identity/orgs/import/preview")
    @Audited(action = "import_dry_run", targetType = "org_import", recordArgs = true)
    public ApiResponse<ImportReport> previewOrgs(@RequestParam(name = "file", required = false) MultipartFile file,
                                                 HttpServletRequest request) {
        return ApiResponse.success(importService.preview(ImportKind.ORG, bytes(file, request)));
    }

    @PostMapping("/api/v1/identity/orgs/import")
    @Audited(action = "import", targetType = "org_import", recordArgs = true, recordAfter = false)
    public ApiResponse<ImportReport> importOrgs(@RequestParam(name = "file", required = false) MultipartFile file,
                                                HttpServletRequest request) {
        return ApiResponse.success(importService.commit(ImportKind.ORG, bytes(file, request)));
    }

    // ------------------------------------------------------------------ ② 人员

    @PostMapping("/api/v1/identity/users/import/preview")
    @Audited(action = "import_dry_run", targetType = "user_import", recordArgs = true)
    public ApiResponse<ImportReport> previewUsers(@RequestParam(name = "file", required = false) MultipartFile file,
                                                  HttpServletRequest request) {
        return ApiResponse.success(importService.preview(ImportKind.USER, bytes(file, request)));
    }

    @PostMapping("/api/v1/identity/users/import")
    @Audited(action = "import", targetType = "user_import", recordArgs = true, recordAfter = false)
    public ApiResponse<ImportReport> importUsers(@RequestParam(name = "file", required = false) MultipartFile file,
                                                 HttpServletRequest request) {
        return ApiResponse.success(importService.commit(ImportKind.USER, bytes(file, request)));
    }

    // ------------------------------------------------------------------ ③ 组织负责人

    @PostMapping("/api/v1/identity/org-leaders/import/preview")
    @Audited(action = "import_dry_run", targetType = "org_leader_import", recordArgs = true)
    public ApiResponse<ImportReport> previewOrgLeaders(@RequestParam(name = "file", required = false) MultipartFile file,
                                                       HttpServletRequest request) {
        return ApiResponse.success(importService.preview(ImportKind.ORG_LEADER, bytes(file, request)));
    }

    @PostMapping("/api/v1/identity/org-leaders/import")
    @Audited(action = "import", targetType = "org_leader_import", recordArgs = true, recordAfter = false)
    public ApiResponse<ImportReport> importOrgLeaders(@RequestParam(name = "file", required = false) MultipartFile file,
                                                      HttpServletRequest request) {
        return ApiResponse.success(importService.commit(ImportKind.ORG_LEADER, bytes(file, request)));
    }

    // ------------------------------------------------------------------ ④ 岗位任职

    @PostMapping("/api/v1/identity/user-positions/import/preview")
    @Audited(action = "import_dry_run", targetType = "user_position_import", recordArgs = true)
    public ApiResponse<ImportReport> previewUserPositions(
            @RequestParam(name = "file", required = false) MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(importService.preview(ImportKind.USER_POSITION, bytes(file, request)));
    }

    @PostMapping("/api/v1/identity/user-positions/import")
    @Audited(action = "import", targetType = "user_position_import", recordArgs = true, recordAfter = false)
    public ApiResponse<ImportReport> importUserPositions(
            @RequestParam(name = "file", required = false) MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(importService.commit(ImportKind.USER_POSITION, bytes(file, request)));
    }

    // ------------------------------------------------------------------ ⑤ 角色分配

    @PostMapping("/api/v1/identity/user-roles/import/preview")
    @Audited(action = "import_dry_run", targetType = "user_role_import", recordArgs = true)
    public ApiResponse<ImportReport> previewUserRoles(@RequestParam(name = "file", required = false) MultipartFile file,
                                                      HttpServletRequest request) {
        return ApiResponse.success(importService.preview(ImportKind.USER_ROLE, bytes(file, request)));
    }

    @PostMapping("/api/v1/identity/user-roles/import")
    @Audited(action = "import", targetType = "user_role_import", recordArgs = true, recordAfter = false)
    public ApiResponse<ImportReport> importUserRoles(@RequestParam(name = "file", required = false) MultipartFile file,
                                                     HttpServletRequest request) {
        return ApiResponse.success(importService.commit(ImportKind.USER_ROLE, bytes(file, request)));
    }

    // ------------------------------------------------------------------ 受影响在途清单（§7）

    /** 受影响在途单据清单预检（dry-run，不落库）。 */
    @PostMapping("/api/v1/admin/bulk-import/impact-preview")
    @Audited(action = "import_dry_run", targetType = "org_import", recordArgs = true)
    public ApiResponse<ImportReport> impactPreview(
            @RequestParam(name = "kind", required = false, defaultValue = "org") String kind,
            @RequestParam(name = "file", required = false) MultipartFile file, HttpServletRequest request) {
        ImportKind importKind = ImportKind.of(kind);
        if (importKind == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知的导入类型：" + kind);
        }
        return ApiResponse.success(importService.impactPreview(importKind, bytes(file, request)));
    }

    // ------------------------------------------------------------------ 导出（§9）

    @GetMapping("/api/v1/identity/org-leaders/export")
    @Audited(action = "export", targetType = "org_leader_import", recordAfter = false)
    public ResponseEntity<byte[]> exportOrgLeaders() {
        return csv(exportService.exportOrgLeaders(), "org_leader.csv");
    }

    @GetMapping("/api/v1/identity/user-positions/export")
    @Audited(action = "export", targetType = "user_position_import", recordAfter = false)
    public ResponseEntity<byte[]> exportUserPositions() {
        return csv(exportService.exportUserPositions(), "user_position.csv");
    }

    @GetMapping("/api/v1/identity/user-roles/export")
    @Audited(action = "export", targetType = "user_role_import", recordAfter = false)
    public ResponseEntity<byte[]> exportUserRoles() {
        return csv(exportService.exportUserRoles(), "user_role.csv");
    }

    @GetMapping("/api/v1/admin/audit-logs/export")
    @Audited(action = "export", targetType = "audit_log", recordAfter = false)
    public ResponseEntity<byte[]> exportAuditLogs() {
        return csv(exportService.exportAuditLogs(), "audit-log.csv");
    }

    // ------------------------------------------------------------------ 内部

    private static ResponseEntity<byte[]> csv(String content, String fileName) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * 读取导入文件：优先 {@code multipart/form-data} 的 {@code file} 字段，
     * 否则把请求体当作 CSV 原文（脚本/实测用）。
     */
    private static byte[] bytes(MultipartFile file, HttpServletRequest request) {
        try {
            if (file != null && !file.isEmpty()) {
                return file.getBytes();
            }
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length == 0) {
                throw new BizException(ErrorCode.IMPORT_FILE_INVALID,
                        "未收到导入文件：请以 multipart/form-data 的 file 字段上传，或直接以 CSV 作为请求体");
            }
            return body;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.IMPORT_FILE_INVALID, "读取导入文件失败：" + ex.getMessage(), ex);
        }
    }

    /** 导入类型清单（供前端渲染五步流水线；与路由表 ①②③④⑤ 一一对应）。 */
    @GetMapping("/api/v1/admin/bulk-import/kinds")
    public ApiResponse<java.util.List<Map<String, Object>>> kinds() {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (ImportKind kind : ImportKind.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", kind.code());
            item.put("label", kind.label());
            item.put("file", kind.fileName());
            item.put("columns", kind.columns());
            item.put("previewRoute", kind.routePrefix() + "/preview");
            item.put("commitRoute", kind.routePrefix());
            items.add(item);
        }
        return ApiResponse.success(items);
    }
}
