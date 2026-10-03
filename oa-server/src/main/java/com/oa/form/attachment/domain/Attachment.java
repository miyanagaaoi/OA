package com.oa.form.attachment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oa.common.scope.DataScopeKind;
import com.oa.common.scope.DataScopeTable;
import java.time.LocalDateTime;

/**
 * 附件元数据（{@code flow_attachment}，doc/data-model.md §6.2）—— <b>阶段 2b.7</b>。
 *
 * <h2>为什么在这里贴 {@link DataScopeTable}（kind = NONE）</h2>
 * <p>本注解的唯一作用是**把表登记进 {@code DataScopeTableRegistry}**，从而让
 * {@code DataScopeInterceptor} 对它启用「受控表裸查询拦截」：任何**没有**
 * {@code /* @dataScope(...) *}{@code /} 标记的 SELECT 一律 40303 {@code DATA_SCOPE_MISSING}。
 *
 * <p>{@code kind = NONE} 表示「本表自身没有可用的数据域列」—— 与 {@code sys_org} /
 * {@code sys_org_leader} / {@code sys_user_position} 同一处理（{@code @DataScopeTable}
 * 的既有用法）：{@code flow_attachment} 只有 {@code instance_id}，没有
 * {@code initiator_id} / {@code initiator_org_path} / {@code initiator_company_id}，
 * 直接写成 {@code table=flow_attachment} 会在运行期拼出 {@code a.initiator_id} →
 * {@code Unknown column}（与 {@code FormMapperXmlTest#markerTablesMatchOwnership} 记录的
 * {@code form_data} 教训**逐字相同**）。
 *
 * <p>因此本表的**真实过滤恒为 `flow_instance`**：本包每条 SELECT 都
 * {@code JOIN flow_instance i ON i.id = a.instance_id}，并带**恰好 1 个**
 * {@code @dataScope(table=flow_instance, alias=i)} 标记（与 {@code FlowRoutingMapper} 同款）。
 * 于是得到两层保护：
 * <ol>
 *   <li><b>真过滤</b>：调用人看不到域外单据的附件（域外查不到 → 404，fail-closed）；</li>
 *   <li><b>防退化</b>：后人若新增一条不带标记的 {@code flow_attachment} 裸查询，
 *       会被拦截器直接拒绝，而不是悄悄绕过数据域。</li>
 * </ol>
 *
 * <p>写语句（INSERT/UPDATE/DELETE）不需要标记（拦截器只约束 SELECT）。
 */
@TableName("flow_attachment")
@DataScopeTable(table = "flow_attachment", alias = "a", kind = DataScopeKind.NONE)
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    /** 补件附件关联的补件请求；原始附件为空。 */
    private Long taskId;

    /** {@code 0} = 原始附件，{@code 1..3} = 第 N 次补件（doc/enums.md §12.3）。 */
    private Integer round;

    /** 对应表单字段 ID（doc/forms.md 字段表；缺省 {@code attachments}）。 */
    private String fieldCode;

    /** **展示名**（原名安全化后；绝不参与服务器路径拼接）。 */
    private String fileName;

    private Long fileSize;

    private String fileExt;

    private String mimeType;

    /** **存储名**（服务端生成的相对路径；与展示名分离）。 */
    private String storagePath;

    private String sha256;

    private Long uploaderId;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileExt() {
        return fileExt;
    }

    public void setFileExt(String fileExt) {
        this.fileExt = fileExt;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 对外视图。
     *
     * <p><b>刻意不含 {@code storagePath}</b>：{@code doc/test-cases.md} TC-FORM-024 的验收形态是
     * 「已知 {@code storage_path} 也无法直连」；把它放进接口出参等于给攻击者递上路径，
     * 而客户端本来也不需要它（下载/预览都走带 id 的鉴权接口）。
     */
    public java.util.Map<String, Object> view() {
        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", id);
        view.put("instanceId", instanceId);
        view.put("round", round);
        view.put("fieldCode", fieldCode);
        view.put("fileName", fileName);
        view.put("fileSize", fileSize);
        view.put("fileExt", fileExt);
        view.put("mimeType", mimeType);
        view.put("sha256", sha256);
        view.put("uploaderId", uploaderId);
        view.put("createdAt", createdAt);
        view.put("downloadUrl", "/api/v1/forms/attachments/" + id + "/download");
        view.put("previewUrl", "/api/v1/forms/attachments/" + id + "/preview");
        return view;
    }
}
