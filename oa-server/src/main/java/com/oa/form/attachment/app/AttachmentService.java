package com.oa.form.attachment.app;

import com.oa.authz.visibility.VisibilityRoles;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormWritePolicy;
import com.oa.form.attachment.domain.Attachment;
import com.oa.form.attachment.domain.AttachmentPolicy;
import com.oa.form.attachment.domain.AttachmentPolicy.Bounds;
import com.oa.form.attachment.domain.AttachmentPolicy.Report;
import com.oa.form.attachment.infra.AttachmentMapper;
import com.oa.form.template.schema.FormFieldDef;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.form.template.writemodel.FormStateWriteGuard;
import com.oa.form.template.writemodel.WriteContext;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.row.FlowSupplementRow;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * <b>阶段 2b.7 —— 附件上传 / 鉴权下载 / 删除的编排层</b>。
 *
 * <h2>这一层要证明的事</h2>
 * <p>「服务端是边界」：前端置灰、客户端声明的文件名与 MIME、容器声明的 Content-Type
 * <b>一律不作数</b>。所有判定都在这里或它调用的纯函数里完成，且**失败不留痕**
 * （不落 {@code flow_attachment}、不留物理文件）。
 *
 * <h2>固定顺序（顺序本身是口径）</h2>
 * <ol>
 *   <li><b>数据域</b>：{@code FlowInstanceMapper#selectInstanceById}（受控表 + 标记，
 *       域外查不到 → 404，AC-02 / AC-17 / TC-AUTH-026）；</li>
 *   <li><b>字段归属</b>：字段必须在该实例**锁定版本**的 schema 里且类型是
 *       {@code file}/{@code files}（AC-09 + 40308 禁止夹带未登记字段）；</li>
 *   <li><b>三态白名单</b>：复用 {@link FormStateWriteGuard}（草稿 / 待补件可传；
 *       **审批中与已完结一律 40304**）—— 附件绝不是绕过三态白名单的写入通道；</li>
 *   <li><b>身份</b>：发起人本人或系统管理员（补件态 forms.md §8「仅发起人可补附件与备注」）；</li>
 *   <li><b>三档限额 + 双校验</b>：{@link AttachmentPolicy}（纯函数，可穷举单测）；</li>
 *   <li><b>落盘 → 落库</b>：任一步失败即回滚并清理已落盘文件（不留垃圾文件）。</li>
 * </ol>
 *
 * <h2>删除的取舍（本工作包要求显式说明）</h2>
 * <p><b>先删物理文件，再删元数据行</b>（同一事务）：
 * <ul>
 *   <li>文件删除失败 → 抛 50004 → 事务回滚 → **元数据行保留**，用户可重试，磁盘与库仍一致；</li>
 *   <li>元数据删除失败（DB 异常） → 事务回滚 → 元数据保留，但文件已删 → 悬挂行；
 *       下载时按 404 fail-closed（{@code LocalAttachmentStorage#resolveExisting}），不泄露内部细节；</li>
 *   <li>反过来（先删元数据再删文件）会产生「**无人引用却仍占空间**」的孤儿文件 ——
 *       那正是 AC-45「不留垃圾文件」要避免的形态，且没有任何调用方会发现它。</li>
 * </ul>
 *
 * <p><b>软删还是硬删</b>：{@code flow_attachment} 没有 {@code deleted_at} / {@code status} 列
 * （doc/data-model.md §6.2 的 DDL 为准），一刀不动真源表结构 ⇒ 采用**硬删元数据**
 * （物理删除文件 + 删除行）。若将来业务要求"删除留痕/可恢复"，正确做法是**走真源闭环**加列，
 * 不是在本层自造软删语义。
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    /** 默认附件字段码（doc/forms.md 四类单据的字段表恒为 {@code attachments}）。 */
    public static final String DEFAULT_FIELD_CODE = FormWritePolicy.FIELD_ATTACHMENTS;

    /** 嗅探所需的最大头字节数。 */
    private static final int SNIFF_BYTES = 32;

    /** 补件轮次上限（doc/enums.md §12.3：0..3）。 */
    private static final int MAX_ROUND = 3;

    private final AttachmentMapper attachmentMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FormSchemaService schemaService;
    private final FormStateWriteGuard writeGuard;
    private final AttachmentStorage storage;
    private final FlowRuntimeMapper runtimeMapper;
    private final AuditLogWriter auditLogWriter;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AttachmentService(AttachmentMapper attachmentMapper,
                             FlowInstanceMapper instanceMapper,
                             FormSchemaService schemaService,
                             FormStateWriteGuard writeGuard,
                             AttachmentStorage storage,
                             FlowRuntimeMapper runtimeMapper,
                             AuditLogWriter auditLogWriter) {
        this.attachmentMapper = attachmentMapper;
        this.instanceMapper = instanceMapper;
        this.schemaService = schemaService;
        this.writeGuard = writeGuard;
        this.storage = storage;
        this.runtimeMapper = runtimeMapper;
        this.auditLogWriter = auditLogWriter;
    }

    // ================================================================ 上传

    /**
     * 上传附件（**多文件一次请求**，§1.4「单次上传数量 ≤ 20 个」按本次请求计数）。
     *
     * @param instanceId 单据 id（数据域内）
     * @param fieldCode  目标字段（缺省 {@code attachments}）
     * @param files      multipart 文件（至少 1 个）
     */
    @Transactional
    public Map<String, Object> upload(Long instanceId, String fieldCode, List<MultipartFile> files,
                                      CurrentUser principal) {
        FlowInstanceRow instance = requireInstance(instanceId);
        FormSchema schema = schemaService.forInstance(instance);
        FormFieldDef field = requireAttachmentField(schema, fieldCode);
        WriteContext context = writeGuard.contextOf(instance, principal, schema.fieldCodes());

        // ③ 三态白名单（审批中 / 已完结 → 40304；附件不是绕过白名单的通道）
        writeGuard.assertStateWritable(Set.of(field.code()), context);
        // ④ 身份：发起人本人或系统管理员
        requireInitiatorOrAdmin(instance, principal, "上传附件");

        List<MultipartFile> incoming = files == null ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        Bounds bounds = AttachmentPolicy.boundsOf(field);
        if (incoming.isEmpty()) {
            // 空 multipart（含"用户什么都没选"）→ 400，不静默成功
            throw new BizException(ErrorCode.ATTACHMENT_POLICY_DENIED, "未提供任何文件")
                    .withDetail("instanceId", instanceId);
        }
        // ⑤ 三档限额：单次数量 / 单字段累计 / 单据合计
        failIfDenied(AttachmentPolicy.checkPerUpload(bounds, incoming.size()), instanceId);
        int existingInField = attachmentMapper.countByInstanceAndField(instanceId, field.code());
        int existingInInstance = attachmentMapper.countByInstance(instanceId);
        failIfDenied(AttachmentPolicy.checkFieldCount(bounds, existingInField, incoming.size()), instanceId);
        failIfDenied(AttachmentPolicy.checkInstanceCount(bounds, existingInInstance, incoming.size()), instanceId);

        int round = resolveRound(instance);
        List<AttachmentStorage.StoredFile> stored = new ArrayList<>();
        List<Attachment> rows = new ArrayList<>();
        // ① **先全量校验**：批量里任一项不合格就整体拒绝 —— 校验阶段不落任何字节，
        //    因此"批内第 2 个是 .exe"不会留下第 1 个已落盘的文件（AC-45「不留垃圾文件」）
        for (MultipartFile file : incoming) {
            byte[] head = readHead(file);
            failIfDenied(AttachmentPolicy.check(bounds, file.getOriginalFilename(), file.getContentType(),
                    file.getSize(), head), instanceId);
        }
        // ② 再落盘 + 落库；任一步失败即回滚（DB 走事务，文件显式清理）
        try {
            for (MultipartFile file : incoming) {
                String rawName = file.getOriginalFilename();
                String ext = AttachmentPolicy.extensionOf(rawName);
                AttachmentStorage.StoredFile result;
                try (InputStream in = file.getInputStream()) {
                    result = storage.store(ext, in, file.getSize());
                } catch (IOException ex) {
                    throw new BizException(ErrorCode.ATTACHMENT_STORAGE_FAILED,
                            "附件读取失败：" + ex.getClass().getSimpleName());
                }
                stored.add(result);

                Attachment row = new Attachment();
                row.setInstanceId(instanceId);
                // task_id 留空：补件请求在 flow_supplement 里以 node_instance_id 表达，
                // 没有可直接引用的 flow_task id（见交付说明「待决策」），不猜。
                row.setTaskId(null);
                row.setRound(round);
                row.setFieldCode(field.code());
                row.setFileName(AttachmentPolicy.safeDisplayName(rawName));
                row.setFileSize(result.sizeBytes());
                row.setFileExt(ext);
                row.setMimeType(truncate(file.getContentType(), 128));
                row.setStoragePath(result.relativePath());
                row.setSha256(result.sha256());
                row.setUploaderId(principal == null ? null : principal.id());
                attachmentMapper.insert(row);
                rows.add(row);
            }
        } catch (RuntimeException ex) {
            // 失败即清理：DB 由事务回滚，物理文件必须显式删（文件系统不参与事务）
            rollbackStored(stored);
            throw ex;
        }

        auditLogWriter.appendAsCurrentUser("attachment_upload", "flow_instance", instanceId,
                null, jsonOf(rows), null, null);
        log.info("附件上传：instanceId={} fieldCode={} round={} 数量={} 上传人={}",
                instanceId, field.code(), round, rows.size(), principal == null ? null : principal.id());

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("instanceId", instanceId);
        view.put("fieldCode", field.code());
        view.put("round", round);
        view.put("uploaded", rows.stream().map(Attachment::view).toList());
        view.put("fieldCount", existingInField + rows.size());
        view.put("instanceCount", existingInInstance + rows.size());
        view.put("bounds", boundsView(bounds));
        view.put("evidence", "doc/forms.md §1.4 附件通用限制；AC-45（REQ-FORM-002）");
        return view;
    }

    // ================================================================ 查询

    /** 某单据的附件清单（数据域过滤；**不含存储路径**）。 */
    public Map<String, Object> list(Long instanceId) {
        FlowInstanceRow instance = requireInstance(instanceId);
        List<Attachment> rows = attachmentMapper.selectByInstance(instanceId);
        Map<Integer, List<Map<String, Object>>> byRound = new LinkedHashMap<>();
        for (Attachment row : rows) {
            byRound.computeIfAbsent(row.getRound() == null ? 0 : row.getRound(), key -> new ArrayList<>())
                    .add(row.view());
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("instanceId", instanceId);
        view.put("bizNo", instance.getBizNo());
        view.put("total", rows.size());
        view.put("rounds", byRound);
        view.put("evidence", "doc/enums.md §12.3 附件轮次语义（0=原始附件，1..3=第 N 次补件）");
        return view;
    }

    /**
     * 取一个附件的**内容句柄**（下载与预览共用）。
     *
     * <p>鉴权与数据域已在 {@link #requireAttachment} 内完成：域外/不存在一律 404
     * （不区分，避免成为 id 枚举通道）。
     */
    public AttachmentContent open(Long attachmentId) {
        Attachment attachment = requireAttachment(attachmentId);
        InputStream stream = storage.open(attachment.getStoragePath());
        return new AttachmentContent(attachment, stream);
    }

    // ================================================================ 删除

    /**
     * 删除附件（仅上传者本人或系统管理员；且仅在三态允许的窗口内）。
     *
     * <p>取舍见类注释：「先删物理文件，再删元数据行」。
     */
    @Transactional
    public Map<String, Object> delete(Long attachmentId, CurrentUser principal) {
        Attachment attachment = requireAttachment(attachmentId);
        FlowInstanceRow instance = requireInstance(attachment.getInstanceId());
        FormSchema schema = schemaService.forInstance(instance);
        WriteContext context = writeGuard.contextOf(instance, principal, schema.fieldCodes());

        // 三态窗口（审批中 / 已完结 → 40304）：与上传同一判据，不给"删了再传"的旁路
        String fieldCode = attachment.getFieldCode() == null ? DEFAULT_FIELD_CODE : attachment.getFieldCode();
        writeGuard.assertStateWritable(Set.of(fieldCode), context);
        // 上传者本人或系统管理员（AC-41 最小权限）
        if (!isAdmin(principal) && !isUploader(attachment, principal)) {
            throw new BizException(ErrorCode.ATTACHMENT_DELETE_DENIED)
                    .withDetail("attachmentId", attachmentId)
                    .withDetail("uploaderId", attachment.getUploaderId())
                    .withDetail("operatorId", principal == null ? null : principal.id());
        }

        // ① 先删物理文件（失败 → 50004 → 事务回滚，元数据保留，可重试）
        storage.delete(attachment.getStoragePath());
        // ② 再删元数据
        int deleted = attachmentMapper.deleteById(attachmentId);
        if (deleted != 1) {
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND, ErrorCode.ATTACHMENT_NOT_FOUND.getMessage());
        }
        auditLogWriter.appendAsCurrentUser("attachment_delete", "flow_attachment", attachmentId,
                jsonOf(List.of(attachment)), null, null, null);
        log.info("附件删除：id={} instanceId={} 文件名={} 操作人={}",
                attachmentId, attachment.getInstanceId(), attachment.getFileName(),
                principal == null ? null : principal.id());

        Map<String, Object> view = attachment.view();
        view.put("deleted", true);
        view.put("physicalFileRemoved", !storage.exists(attachment.getStoragePath()));
        return view;
    }

    // ================================================================ 内部：鉴权与取数

    /** 数据域过滤：域外实例查不到（404；AC-02 / AC-17）。 */
    private FlowInstanceRow requireInstance(Long instanceId) {
        if (instanceId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "instanceId 不能为空");
        }
        FlowInstanceRow instance = instanceMapper.selectInstanceById(instanceId);
        if (instance == null) {
            throw BizException.notFound("流程实例");
        }
        return instance;
    }

    /** 数据域过滤：域外附件查不到（404，与"不存在"同码，AC-41 不泄露）。 */
    private Attachment requireAttachment(Long attachmentId) {
        if (attachmentId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "attachmentId 不能为空");
        }
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND, ErrorCode.ATTACHMENT_NOT_FOUND.getMessage())
                    .withDetail("attachmentId", attachmentId);
        }
        return attachment;
    }

    /** 字段必须在该实例**锁定版本**的 schema 内且是附件类型（40308 禁止夹带未登记字段）。 */
    private FormFieldDef requireAttachmentField(FormSchema schema, String fieldCode) {
        String code = fieldCode == null || fieldCode.isBlank() ? DEFAULT_FIELD_CODE : fieldCode.trim();
        FormFieldDef field = schema.fields().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElse(null);
        if (field == null) {
            throw new BizException(ErrorCode.FIELD_NOT_IN_SCHEMA,
                    String.format(ErrorCode.FIELD_NOT_IN_SCHEMA.getMessage(), code)
                            + "（附件只能挂在实例锁定版本 schema 里已登记的 file/files 字段上）")
                    .withDetail("fieldCode", code)
                    .withDetail("schemaFields", schema.fieldCodes());
        }
        if (!field.isAttachment()) {
            throw new BizException(ErrorCode.ATTACHMENT_POLICY_DENIED,
                    String.format("字段 %s（%s）不是附件字段（type=%s），不能上传附件",
                            field.code(), field.label(), field.type() == null ? "null" : field.type().code()))
                    .withDetail("fieldCode", field.code());
        }
        return field;
    }

    /**
     * 上传/删除的身份闸门：**发起人本人或系统管理员**。
     *
     * <p>为什么不只靠三态白名单：{@code FormWritePolicy.writableFields} 在草稿态返回**全部字段**
     * （它判"状态"，不判"人"）；若只调它，任何数据域内能看到该草稿的人都能往别人的单据里塞附件。
     * 故此处再判身份 —— 与 {@code FormDataService#requireWriter} 同口径
     * （发起人 / 系统管理员；印鉴单归还登记的节点⑦例外**不适用**于附件）。
     */
    private void requireInitiatorOrAdmin(FlowInstanceRow instance, CurrentUser principal, String action) {
        if (principal == null || principal.id() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (isAdmin(principal)) {
            return;
        }
        if (principal.id().equals(instance.getInitiatorId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "只有发起人本人或系统管理员可以" + action)
                .withDetail("instanceId", instance.getId())
                .withDetail("initiatorId", instance.getInitiatorId())
                .withDetail("operatorId", principal.id());
    }

    private static boolean isAdmin(CurrentUser principal) {
        return principal != null && principal.id() != null && principal.hasRole(VisibilityRoles.ADMIN);
    }

    private static boolean isUploader(Attachment attachment, CurrentUser principal) {
        return principal != null && principal.id() != null
                && principal.id().equals(attachment.getUploaderId());
    }

    /**
     * 补件轮次（{@code doc/enums.md} §12.3）。
     *
     * <ul>
     *   <li>草稿 → {@code 0}（原始附件）；</li>
     *   <li>待补件 → 取 {@code flow_supplement} 中**处理中**那条的 {@code supplement_round}
     *       （{@code doc/data-model.md} §5.5：「{@code supplement_round} 在**请求补件时占位**
     *       （第 N 次请求即第 N 轮）」）；查不到时退化为 {@code supplement_count + 1} 并夹到 {@code 1..3}；</li>
     *   <li>其余状态不会走到这里（三态白名单已拦）。</li>
     * </ul>
     */
    private int resolveRound(FlowInstanceRow instance) {
        FormWritePolicy.FormState state = FormWritePolicy.resolveState(instance.getStatus(), instance.getSubStatus());
        if (state != FormWritePolicy.FormState.PENDING_SUPPLEMENT) {
            return 0;
        }
        if (runtimeMapper != null) {
            try {
                FlowSupplementRow pending = runtimeMapper.selectPendingSupplement(instance.getId());
                if (pending != null && pending.getSupplementRound() != null) {
                    return clampRound(pending.getSupplementRound());
                }
            } catch (RuntimeException ex) {
                log.warn("读取补件请求失败（instanceId={}），按 supplement_count 推算轮次：{}",
                        instance.getId(), ex.getMessage());
            }
        }
        int count = instance.getSupplementCount() == null ? 0 : instance.getSupplementCount();
        return clampRound(count + 1);
    }

    private static int clampRound(Integer round) {
        if (round == null || round < 1) {
            return 1;
        }
        return Math.min(round, MAX_ROUND);
    }

    // ================================================================ 内部：杂项

    private void failIfDenied(Report report, Long instanceId) {
        if (report != null && !report.ok()) {
            throw new BizException(ErrorCode.ATTACHMENT_POLICY_DENIED, report.message())
                    .withDetail("instanceId", instanceId);
        }
    }

    /** 读文件头（不消费整个流；{@link MultipartFile} 支持重复取流）。 */
    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.ATTACHMENT_STORAGE_FAILED,
                    "附件读取失败：" + ex.getClass().getSimpleName());
        }
    }

    /** 回滚已落盘的物理文件（DB 由事务回滚；文件系统不参与事务，必须显式清）。 */
    private void rollbackStored(List<AttachmentStorage.StoredFile> stored) {
        for (AttachmentStorage.StoredFile file : stored) {
            try {
                storage.delete(file.relativePath());
            } catch (RuntimeException ex) {
                // 清理失败不回滚原始异常，但必须留 ERROR 日志（审计缺口需可发现）
                log.error("上传失败后清理物理文件也失败（需人工确认）：path={} error={}",
                        file.relativePath(), ex.getMessage());
            }
        }
    }

    private static Map<String, Object> boundsView(Bounds bounds) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("maxSizeMb", bounds.maxSizeMb());
        view.put("maxCount", bounds.maxCount());
        view.put("maxPerUpload", bounds.maxPerUpload());
        view.put("maxPerInstance", bounds.maxPerInstance());
        view.put("allowExt", new java.util.TreeSet<>(bounds.allowExt()));
        view.put("denyExt", new java.util.TreeSet<>(bounds.denyExt()));
        return view;
    }

    private static String jsonOf(List<Attachment> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Attachment row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("instanceId", row.getInstanceId());
            item.put("round", row.getRound());
            item.put("fieldCode", row.getFieldCode());
            item.put("fileName", row.getFileName());
            item.put("fileSize", row.getFileSize());
            item.put("fileExt", row.getFileExt());
            item.put("sha256", row.getSha256());
            list.add(item);
        }
        return com.oa.common.json.JsonText.write(list);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 附件内容句柄（附件元数据 + 打开的流）。
     *
     * <p>{@code mimeType} 取库里的**服务端记录**（上传时落库），不是客户端的原始声明 ——
     * 客户端可能先上传 {@code image/png} 再在下载时声称别的类型。
     */
    public record AttachmentContent(Attachment attachment, InputStream stream) {
    }
}
