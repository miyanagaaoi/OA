package com.oa.form.attachment.api;

import com.oa.common.audit.Audited;
import com.oa.common.config.OaProperties;
import com.oa.common.security.CurrentUser;
import com.oa.form.attachment.app.AttachmentService;
import com.oa.form.attachment.app.AttachmentService.AttachmentContent;
import com.oa.form.attachment.domain.Attachment;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * <b>阶段 2b.7 —— 附件接口</b>（{@code oa.form.template.attachment.*}）。
 *
 * <h2>路由清单</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>入口闸门</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>{@code /forms/instances/{id}/attachments}</td><td>{@code flow}</td>
 *       <td>上传（multipart；三档限额 + 格式双校验 + 内容嗅探）</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/instances/{id}/attachments}</td>
 *       <td>{@code flow} ∪ {@code admin:flow}</td><td>附件清单（按轮次分组；**不含存储路径**）</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/attachments/{id}/download}</td>
 *       <td>{@code flow} ∪ {@code admin:flow}</td><td>鉴权下载（**恒为 attachment**，AC-45）</td></tr>
 *   <tr><td>GET</td><td>{@code /forms/attachments/{id}/preview}</td>
 *       <td>{@code flow} ∪ {@code admin:flow}</td><td>预览（仅安全类型内联，其余降级为下载）</td></tr>
 *   <tr><td>DELETE</td><td>{@code /forms/attachments/{id}}</td><td>{@code flow}</td>
 *       <td>删除（仅上传者本人或管理员，且仅在三态窗口内）</td></tr>
 * </table>
 *
 * <h2>为什么没有直链</h2>
 * <p>真源 {@code doc/forms.md} §1.4「下载必须经鉴权接口，**禁止直链**」+
 * {@code doc/test-cases.md} TC-FORM-024「直接访问 {@code storage_path} 对应的静态地址 → 返回 403」。
 * 本工程**不注册任何静态资源映射**（{@code WebMvcConfig} 只有拦截器与 CORS），
 * 私有目录默认在 {@code ${user.home}/.oa/attachments}（仓库之外），因此
 * {@code storage_path} 对应的文件**没有任何 URL 可以触达** —— 拓扑上不可能，而不是靠约定。
 *
 * <h2>响应头安全</h2>
 * <ul>
 *   <li>{@code Content-Disposition}：文件名按 RFC 5987 编码成 {@code filename*=UTF-8''…}，
 *       同时给一个已剔除引号/反斜杠/控制字符的 ASCII 回退名 —— 防止响应头注入
 *       （文件名来自客户端，含 {@code "} 或换行即可撕裂响应头）；</li>
 *   <li>{@code Content-Type}：取**服务端落库**的 MIME，且仅当它属于安全内联白名单
 *       （{@code image/jpeg} / {@code image/png} / {@code application/pdf}）才允许 {@code inline}；
 *       其余（含 {@code image/svg+xml}、{@code text/html}、未知类型）**一律降级为 attachment**；</li>
 *   <li>{@code X-Content-Type-Options: nosniff}：禁止浏览器按内容猜类型
 *       （否则声明的 {@code text/plain} 也可能被渲染成 HTML）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/forms")
public class AttachmentController {

    /** 允许**内联预览**的 MIME（白名单；其余一律 attachment 降级）。 */
    private static final Set<String> INLINE_SAFE_MIME = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    /** 无 MIME 记录时的兜底类型（最保守：二进制流，不可渲染）。 */
    private static final MediaType FALLBACK_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private final AttachmentService attachmentService;
    private final WorkflowPermissionService permissionService;
    private final OaProperties properties;

    public AttachmentController(AttachmentService attachmentService,
                                WorkflowPermissionService permissionService,
                                OaProperties properties) {
        this.attachmentService = attachmentService;
        this.permissionService = permissionService;
        this.properties = properties;
    }

    // ================================================================ 上传 / 清单

    /** 上传附件（multipart，字段名 {@code files}；可选 {@code fieldCode}）。 */
    @PostMapping("/instances/{instanceId}/attachments")
    @Audited(action = "attachment_upload", targetType = "flow_instance", targetId = "#instanceId")
    public com.oa.common.api.ApiResponse<Map<String, Object>> upload(
            @PathVariable("instanceId") Long instanceId,
            @RequestParam(name = "fieldCode", required = false) String fieldCode,
            @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        // 入口闸门：写入口取该动作自己的权限码 flow（与 FormDataController#save 同口径）
        CurrentUser principal = permissionService.requirePermission("上传附件", FlowConfigPermission.FLOW_USE);
        return com.oa.common.api.ApiResponse.success(
                attachmentService.upload(instanceId, fieldCode, files, principal));
    }

    /** 附件清单（按轮次分组；出参**不含 storage_path**）。 */
    @GetMapping("/instances/{instanceId}/attachments")
    public com.oa.common.api.ApiResponse<Map<String, Object>> list(
            @PathVariable("instanceId") Long instanceId) {
        permissionService.requireInitiator("查看附件清单");
        return com.oa.common.api.ApiResponse.success(attachmentService.list(instanceId));
    }

    // ================================================================ 下载 / 预览

    /** 鉴权下载（恒为 {@code attachment}；AC-45「经鉴权接口下载成功且写入日志」）。 */
    @GetMapping("/attachments/{attachmentId}/download")
    @Audited(action = "attachment_download", targetType = "flow_attachment",
            targetId = "#attachmentId", recordAfter = false)
    public ResponseEntity<Resource> download(@PathVariable("attachmentId") Long attachmentId) {
        permissionService.requireInitiator("下载附件");
        return stream(attachmentId, false);
    }

    /**
     * 预览：**仅安全类型内联**，其余降级为下载。
     *
     * <p>真源：{@code doc/enums.md} §12.1 的降级口径要求 {@code heic} 转 {@code jpg} 后预览、
     * {@code wps} 提示下载查看。
     *
     * <p><b>HEIC 转码是「已登记的未实现项」（2026-10 裁定，不再是「待决策」）</b>：
     * 需要图像处理依赖，真源未指定实现方式 ⇒ 本轮 {@code heic} 走 {@code attachment} 降级
     * （不内联渲染、不报错）。已**排期阶段 3**，与电子签名一起引入图像处理依赖（避免装两次）；
     * 真源已如实回写于 {@code doc/enums.md} §12.1（A-03）、{@code doc/forms.md} §1.4「预览降级」行，
     * 可执行用例 {@code doc/test-cases.md} TC-FORM-033（其「转 jpg 预览」一项未实现前**不得判通过**）。
     */
    @GetMapping("/attachments/{attachmentId}/preview")
    @Audited(action = "attachment_preview", targetType = "flow_attachment",
            targetId = "#attachmentId", recordAfter = false)
    public ResponseEntity<Resource> preview(@PathVariable("attachmentId") Long attachmentId) {
        permissionService.requireInitiator("预览附件");
        return stream(attachmentId, true);
    }

    // ================================================================ 删除

    /** 删除附件（仅上传者本人或管理员，且仅在三态窗口内）。 */
    @DeleteMapping("/attachments/{attachmentId}")
    @Audited(action = "attachment_delete", targetType = "flow_attachment", targetId = "#attachmentId")
    public com.oa.common.api.ApiResponse<Map<String, Object>> delete(
            @PathVariable("attachmentId") Long attachmentId) {
        CurrentUser principal = permissionService.requirePermission("删除附件", FlowConfigPermission.FLOW_USE);
        return com.oa.common.api.ApiResponse.success(attachmentService.delete(attachmentId, principal));
    }

    // ================================================================ 内部：响应装配

    private ResponseEntity<Resource> stream(Long attachmentId, boolean preview) {
        AttachmentContent content = attachmentService.open(attachmentId);
        Attachment attachment = content.attachment();
        InputStream stream = content.stream();
        try {
            long length = attachment.getFileSize() == null ? -1L : attachment.getFileSize();
            MediaType type = safeMediaType(attachment.getMimeType());
            boolean inline = preview && INLINE_SAFE_MIME.contains(type.toString())
                    && length >= 0 && length <= properties.getAttachment().getMaxInlinePreviewBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(type);
            headers.setContentLength(length < 0 ? 0 : length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(attachment.getFileName(), inline));
            // 禁止浏览器按内容嗅探类型（否则声明成 text/plain 的 HTML 也会被渲染）
            headers.set("X-Content-Type-Options", "nosniff");
            if (inline) {
                // 内联渲染时再上一道沙箱：即使内容被构造成脚本也无法访问同源资源
                headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
            }
            return new ResponseEntity<>(new InputStreamResource(stream), headers,
                    inline ? HttpStatus.OK : HttpStatus.OK);
        } catch (RuntimeException ex) {
            closeQuietly(stream);
            throw ex;
        }
    }

    /**
     * 响应类型安全化：**只信服务端落库的 MIME**，且不在内联白名单内时保留原类型但强制下载。
     *
     * <p>注意：即使 MIME 是 {@code text/html}，本方法也**不改写** Content-Type（免得客户端
     * 下载后类型错乱），安全性由 {@code Content-Disposition: attachment} + {@code nosniff} 保证。
     */
    private static MediaType safeMediaType(String storedMime) {
        if (storedMime == null || storedMime.isBlank()) {
            return FALLBACK_TYPE;
        }
        try {
            return MediaType.parseMediaType(storedMime.trim());
        } catch (RuntimeException ex) {
            return FALLBACK_TYPE;
        }
    }

    /**
     * {@code Content-Disposition} 安全装配（RFC 5987 + ASCII 回退）。
     *
     * <p>客户端原名已经过 {@code AttachmentPolicy.safeDisplayName}（去路径、去控制字符），
     * 但**没有**去掉引号/反斜杠 —— 那属于响应头语境，在此处理：
     * ASCII 回退名剥掉 {@code "} / {@code \} / CR / LF，非 ASCII 字符替换为 {@code _}；
     * 真名走 {@code filename*=UTF-8''<percent-encoded>}。
     */
    static String contentDisposition(String fileName, boolean inline) {
        String raw = fileName == null || fileName.isBlank() ? "attachment" : fileName;
        StringBuilder ascii = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"' || ch == '\\' || ch == '\r' || ch == '\n' || ch < 0x20 || ch == 0x7F) {
                continue;
            }
            ascii.append(ch <= 0x7F ? ch : '_');
        }
        if (ascii.length() == 0) {
            ascii.append("attachment");
        }
        String encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
        return (inline ? "inline" : "attachment")
                + "; filename=\"" + ascii + "\""
                + "; filename*=UTF-8''" + encoded;
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (Exception ignored) {
            // 关闭失败无需上报：错误路径已经在抛原始异常
        }
    }
}
