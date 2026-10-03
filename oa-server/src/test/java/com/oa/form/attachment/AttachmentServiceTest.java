package com.oa.form.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.authz.visibility.FormFieldWriteGuard;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.form.attachment.app.AttachmentService;
import com.oa.form.attachment.app.AttachmentStorage;
import com.oa.form.attachment.app.LocalAttachmentStorage;
import com.oa.form.attachment.domain.Attachment;
import com.oa.form.attachment.infra.AttachmentMapper;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.schema.FormSchemaParser;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.form.template.writemodel.FormStateWriteGuard;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.row.FlowSupplementRow;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * <b>2b.7 附件服务层</b>单测 —— 证明「服务端是边界」。
 *
 * <p>装配是**真件优先**的：
 * <ul>
 *   <li>{@link FormStateWriteGuard} 用**真实现**（它才是三态白名单的强制点）——
 *       因此「审批中上传被拒」不是在测 mock 的返回值；</li>
 *   <li>{@link LocalAttachmentStorage} 用**真实现**（{@code @TempDir}）——
 *       因此「拒绝时不留垃圾文件」「删除后物理文件消失」是**文件系统事实**，不是断言桩调用；</li>
 *   <li>只有 DB 与 schema 取数用桩（无 DB 环境）。</li>
 * </ul>
 *
 * <p>对应用例：TC-FORM-023（三档 + 黑名单/白名单）、TC-FORM-026（单据合计）、
 * TC-FORM-067（补件轮次）、TC-AUTH-026（未登录/域外 → 4xx）、AC-28（审批中只读）。
 */
class AttachmentServiceTest {

    private static final Long INSTANCE_ID = 1001L;
    private static final Long INITIATOR_ID = 208L;
    private static final Long OTHER_USER_ID = 209L;

    /** 1×1 的最小合法 PNG（头 8 字节 + 后续填充），用于「内容与扩展名一致」的正例。 */
    private static final byte[] PNG_BYTES = pngBytes();

    private static final byte[] PDF_BYTES = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);

    private static final byte[] EXE_BYTES = new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0};

    @TempDir
    Path storageRoot;

    private AttachmentMapper attachmentMapper;
    private FlowInstanceMapper instanceMapper;
    private FormSchemaService schemaService;
    private FlowRuntimeMapper runtimeMapper;
    private AuditLogWriter auditLogWriter;
    private LocalAttachmentStorage storage;
    private AttachmentService service;

    private final FormSchema matterSchema = FormSchemaParser.parse(matterSchemaJson(), "matter", 1);

    /** 合同单最小 schema：**两个**附件类字段（{@code attachments} + {@code counterparty_docs}）。 */
    private final FormSchema contractSchema = FormSchemaParser.parse(contractSchemaJson(), "contract", 1);

    @BeforeEach
    void setUp() {
        attachmentMapper = mock(AttachmentMapper.class);
        instanceMapper = mock(FlowInstanceMapper.class);
        schemaService = mock(FormSchemaService.class);
        runtimeMapper = mock(FlowRuntimeMapper.class);
        auditLogWriter = mock(AuditLogWriter.class);

        OaProperties properties = new OaProperties();
        properties.getAttachment().setRoot(storageRoot.toString());
        storage = new LocalAttachmentStorage(properties);

        FormStateWriteGuard writeGuard = new FormStateWriteGuard(new FormFieldWriteGuard(),
                mock(FlowNodeInstanceMapper.class));
        service = new AttachmentService(attachmentMapper, instanceMapper, schemaService, writeGuard,
                storage, runtimeMapper, auditLogWriter);

        when(schemaService.forInstance(any())).thenReturn(matterSchema);
        when(instanceMapper.selectInstanceById(anyLong())).thenAnswer(invocation -> instance("draft", null));
    }

    // ================================================================ 正例

    @Test
    @DisplayName("草稿 + 发起人 + 合法 PDF → 落盘 + 落库（round=0，展示名安全化，存储名与展示名分离）")
    void draftUploadSucceedsAndPersists() throws Exception {
        MultipartFile file = new MockMultipartFile("files", "报告.pdf", "application/pdf", PDF_BYTES);

        Map<String, Object> view = service.upload(INSTANCE_ID, null, List.of(file), initiator());

        assertThat(view.get("round")).isEqualTo(0);
        assertThat(view.get("fieldCode")).isEqualTo("attachments");

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(captor.capture());
        Attachment row = captor.getValue();
        assertThat(row.getFileName()).isEqualTo("报告.pdf");
        assertThat(row.getFileExt()).isEqualTo("pdf");
        assertThat(row.getRound()).isEqualTo(0);
        assertThat(row.getUploaderId()).isEqualTo(INITIATOR_ID);
        assertThat(row.getStoragePath()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.pdf");
        assertThat(row.getStoragePath()).doesNotContain("报告");
        assertThat(row.getFileSize()).isEqualTo(PDF_BYTES.length);
        assertThat(row.getSha256()).hasSize(64);

        // 文件系统事实：确实落了盘、内容一致
        Path physical = storageRoot.resolve(row.getStoragePath());
        assertThat(Files.isRegularFile(physical)).isTrue();
        assertThat(Files.readAllBytes(physical)).isEqualTo(PDF_BYTES);
        assertThat(countFiles(storageRoot)).isEqualTo(1);
    }

    @Test
    @DisplayName("待补件 + 发起人 → 允许，且 round = 补件请求的 supplement_round（TC-FORM-014/067）")
    void pendingSupplementUploadGetsRound() throws Exception {
        when(instanceMapper.selectInstanceById(anyLong()))
                .thenAnswer(invocation -> instance("approving", "pending_supplement"));
        FlowSupplementRow pending = new FlowSupplementRow();
        pending.setId(55L);
        pending.setSupplementRound(2);
        when(runtimeMapper.selectPendingSupplement(INSTANCE_ID)).thenReturn(pending);

        service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "补件.png", "image/png", PNG_BYTES)), initiator());

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("待补件 + 合同单 counterparty_docs（同为 type=files）→ 按**字段类型**放行（B 项裁定）")
    void pendingSupplementUploadsAnyAttachmentTypedField() {
        when(schemaService.forInstance(any())).thenReturn(contractSchema);
        when(instanceMapper.selectInstanceById(anyLong()))
                .thenAnswer(invocation -> instance("approving", "pending_supplement", "contract"));

        Map<String, Object> view = service.upload(INSTANCE_ID, "counterparty_docs",
                List.of(new MockMultipartFile("files", "license.pdf", "application/pdf", PDF_BYTES)), initiator());

        assertThat(view.get("fieldCode")).isEqualTo("counterparty_docs");
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getFieldCode()).isEqualTo("counterparty_docs");
    }

    @Test
    @DisplayName("待补件 + counterparty_docs 附件 → 可删（window 与字段类型两条都放行）")
    void pendingSupplementDeletesAnyAttachmentTypedField() {
        when(schemaService.forInstance(any())).thenReturn(contractSchema);
        when(instanceMapper.selectInstanceById(anyLong()))
                .thenAnswer(invocation -> instance("approving", "pending_supplement", "contract"));
        Attachment row = storedRow("draft");
        row.setFieldCode("counterparty_docs");
        when(attachmentMapper.selectById(7L)).thenReturn(row);
        when(attachmentMapper.deleteById(7L)).thenReturn(1);

        Map<String, Object> view = service.delete(7L, initiator());

        verify(attachmentMapper).deleteById(7L);
        assertThat(storage.exists(row.getStoragePath())).isFalse();
        assertThat(view.get("physicalFileRemoved")).isEqualTo(true);
    }

    @Test
    @DisplayName("审批中 + counterparty_docs → 仍 40304（按类型放行**不放宽**审批中窗口）")
    void approvingDeleteOfAttachmentTypedFieldStillDenied() {
        when(schemaService.forInstance(any())).thenReturn(contractSchema);
        when(instanceMapper.selectInstanceById(anyLong()))
                .thenAnswer(invocation -> instance("approving", null, "contract"));
        Attachment row = storedRow("draft");
        row.setFieldCode("counterparty_docs");
        when(attachmentMapper.selectById(7L)).thenReturn(row);

        assertThatThrownBy(() -> service.delete(7L, initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_WRITE_DENIED));
        verify(attachmentMapper, never()).deleteById(anyLong());
        assertThat(storage.exists(row.getStoragePath())).isTrue();
    }

    // ================================================================ 三态白名单（核心：附件不是旁路）

    @Test
    @DisplayName("审批中上传 → 40304 FIELD_WRITE_DENIED，且不落盘、不落库（AC-28）")
    void approvingUploadIsDeniedByStateWhitelist() {
        when(instanceMapper.selectInstanceById(anyLong()))
                .thenAnswer(invocation -> instance("approving", null));

        assertThatThrownBy(() -> service.upload(INSTANCE_ID, null,
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_WRITE_DENIED));

        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).as("被拒时不得留下任何文件").isZero();
    }

    @Test
    @DisplayName("已完结（approved）上传 → 同样 40304（三态之外一律只读）")
    void closedUploadIsDenied() {
        when(instanceMapper.selectInstanceById(anyLong()))
                .thenAnswer(invocation -> instance("approved", null));

        assertThatThrownBy(() -> service.upload(INSTANCE_ID, null,
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_WRITE_DENIED));
        verify(attachmentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("审批中**删除** → 40304（不给「先删后传」的写旁路）")
    void approvingDeleteIsDenied() {
        Attachment row = storedRow("draft");
        when(attachmentMapper.selectById(7L)).thenReturn(row);
        when(instanceMapper.selectInstanceById(anyLong()))
                .thenAnswer(invocation -> instance("approving", null));

        assertThatThrownBy(() -> service.delete(7L, initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_WRITE_DENIED));
        verify(attachmentMapper, never()).deleteById(anyLong());
    }

    // ================================================================ 越权

    @Test
    @DisplayName("草稿态、非发起人（即便是管理员以外同域用户）→ 403（草稿态白名单放行全部字段，仍需身份闸门）")
    void nonInitiatorUploadIsForbidden() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, null,
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), otherUser()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("系统管理员可上传（与 FormDataService#requireWriter 同口径）")
    void adminCanUpload() {
        service.upload(INSTANCE_ID, null,
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), admin());
        verify(attachmentMapper).insert(any());
    }

    @Test
    @DisplayName("域外实例 → 404 NOT_FOUND（数据域 fail-closed，AC-02/AC-17/TC-AUTH-026）")
    void outOfScopeInstanceIsNotFound() {
        when(instanceMapper.selectInstanceById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.upload(INSTANCE_ID, null,
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(attachmentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("域外附件（selectById 返回 null）→ 40402（与「不存在」同码，不做 id 枚举通道）")
    void outOfScopeAttachmentIsNotFound() {
        when(attachmentMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service.open(999L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("字段不在实例锁定版本 schema 内 → 40308（禁止夹带未登记字段）")
    void unknownFieldIsRejected() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "secret_doc",
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_NOT_IN_SCHEMA));
    }

    @Test
    @DisplayName("字段不是附件类型（title）→ 40012（附件只能挂在 file/files 字段上）")
    void nonAttachmentFieldIsRejected() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "title",
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_POLICY_DENIED));
    }

    @Test
    @DisplayName("删除：三档全不中（既非上传者本人、也非单据发起人本人、也非管理员）→ 40310，文件与元数据都不动")
    void deleteByNonUploaderIsDenied() {
        Attachment row = storedRow("draft");
        when(attachmentMapper.selectById(7L)).thenReturn(row);

        assertThatThrownBy(() -> service.delete(7L, otherUser()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_DELETE_DENIED));
        verify(attachmentMapper, never()).deleteById(anyLong());
        assertThat(storage.exists(row.getStoragePath())).isTrue();
    }

    @Test
    @DisplayName("删除：本人成功 → 元数据删除 + 物理文件消失（顺序：先文件后元数据）")
    void deleteByUploaderRemovesBoth() {
        Attachment row = storedRow("draft");
        when(attachmentMapper.selectById(7L)).thenReturn(row);
        when(attachmentMapper.deleteById(7L)).thenReturn(1);

        Map<String, Object> view = service.delete(7L, initiator());

        verify(attachmentMapper).deleteById(7L);
        assertThat(storage.exists(row.getStoragePath())).as("物理文件必须消失").isFalse();
        assertThat(view.get("deleted")).isEqualTo(true);
        assertThat(view.get("physicalFileRemoved")).isEqualTo(true);
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("删除：管理员可删他人附件")
    void deleteByAdminIsAllowed() {
        Attachment row = storedRow("draft");
        when(attachmentMapper.selectById(7L)).thenReturn(row);
        when(attachmentMapper.deleteById(7L)).thenReturn(1);
        service.delete(7L, admin());
        verify(attachmentMapper).deleteById(7L);
        assertThat(storage.exists(row.getStoragePath())).isFalse();
    }

    @Test
    @DisplayName("删除（E 项裁定）：管理员代传后，**发起人本人**仍可删除 —— 代传只应「多一个能删的人」")
    void deleteByInitiatorAfterAdminUploadIsAllowed() {
        Attachment row = storedRow("draft");
        row.setUploaderId(1L);           // 上传者 = 系统管理员（代传，≠ 发起人 208）
        when(attachmentMapper.selectById(7L)).thenReturn(row);
        when(attachmentMapper.deleteById(7L)).thenReturn(1);

        Map<String, Object> view = service.delete(7L, initiator());

        verify(attachmentMapper).deleteById(7L);
        assertThat(storage.exists(row.getStoragePath())).as("物理文件必须消失").isFalse();
        assertThat(view.get("physicalFileRemoved")).isEqualTo(true);
    }

    @Test
    @DisplayName("删除（E 项裁定不放宽）：数据域内、但既非上传者也非发起人/管理员 → 仍 40310，文件不动")
    void deleteByBystanderAfterAdminUploadIsDenied() {
        Attachment row = storedRow("draft");
        row.setUploaderId(1L);           // 上传者 = 管理员；调用人 = 内勤（既非上传者也非发起人）
        when(attachmentMapper.selectById(7L)).thenReturn(row);

        assertThatThrownBy(() -> service.delete(7L, otherUser()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_DELETE_DENIED))
                .hasMessageContaining("单据发起人本人");
        verify(attachmentMapper, never()).deleteById(anyLong());
        assertThat(storage.exists(row.getStoragePath())).isTrue();
    }

    // ================================================================ 三档限额 + 格式双校验

    @Test
    @DisplayName("第一档·单文件超限（模板 maxSizeMb=1，传 2MB）→ 40012，不留垃圾文件")
    void oversizedFileIsRejected() {
        byte[] big = new byte[2 * 1024 * 1024];
        System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "big.pdf", "application/pdf", big)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage()).contains("单个文件不超过 1MB"));
        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("第二档·单字段数量超限（maxCount=3，已有 3）→ 40012 且文案取模板 message")
    void fieldCountLimitIsEnforced() {
        when(attachmentMapper.countByInstanceAndField(INSTANCE_ID, "attachments")).thenReturn(3);
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage()).isEqualTo("本单最多 3 个附件"));
        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("第三档·单据合计超限（已有 50）→ 40012「含补件」（TC-FORM-026）")
    void instanceCountLimitIsEnforced() {
        when(attachmentMapper.countByInstanceAndField(anyLong(), anyString())).thenReturn(0);
        when(attachmentMapper.countByInstance(INSTANCE_ID)).thenReturn(50);

        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage())
                        .contains("单张单据附件总数不超过 50 个").contains("含补件"));
        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("单次上传 21 个 → 40012「单次上传不超过 20 个」（TC-FORM-023③）")
    void perUploadLimitIsEnforced() {
        List<MultipartFile> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(new MockMultipartFile("files", "f" + i + ".pdf", "application/pdf", PDF_BYTES));
        }
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments", many, initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage()).contains("单次上传不超过 20 个"));
        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("黑名单扩展名 .exe → 40012（上传即拒绝），不留文件")
    void deniedExtensionIsRejected() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "virus.exe", "application/octet-stream", EXE_BYTES)),
                initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage()).contains("不允许上传 exe 格式"));
        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("白名单外（.sh / .txt）→ 40012；黑名单里的 .exe 优先按「不允许上传 exe 格式」")
    void whitelistMissIsRejected() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "deploy.sh", null,
                        "#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8))), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage()).contains("仅支持"));

        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "note.txt", "text/plain",
                        "hello".getBytes(StandardCharsets.UTF_8))), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage()).contains("仅支持"));
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("扩展名与内容不符（.png 里塞 PE 可执行内容）→ 40012，不留文件")
    void contentMismatchIsRejected() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments",
                List.of(new MockMultipartFile("files", "photo.png", "image/png", EXE_BYTES)), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getMessage()).contains("可执行文件"));
        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    @DisplayName("文件名带 ../ 与超长名 → 展示名安全化后落库，存储名与之无关")
    void maliciousFileNameIsSanitized() throws Exception {
        String longName = "很长的名字".repeat(100) + ".pdf";
        service.upload(INSTANCE_ID, "attachments", List.of(
                new MockMultipartFile("files", "../../../../etc/passwd.pdf", "application/pdf", PDF_BYTES),
                new MockMultipartFile("files", longName, "application/pdf", PDF_BYTES)), initiator());

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper, times(2)).insert(captor.capture());
        List<Attachment> rows = captor.getAllValues();
        assertThat(rows.get(0).getFileName()).isEqualTo("passwd.pdf");
        assertThat(rows.get(1).getFileName()).hasSizeLessThanOrEqualTo(255).endsWith(".pdf");
        for (Attachment row : rows) {
            assertThat(row.getStoragePath()).doesNotContain("..").doesNotContain("passwd")
                    .matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.pdf");
        }
        // 落盘文件都在根之下（穿越不可能）
        try (Stream<Path> walk = Files.walk(storageRoot)) {
            assertThat(walk.filter(Files::isRegularFile)
                    .allMatch(path -> path.normalize().startsWith(storageRoot))).isTrue();
        }
    }

    @Test
    @DisplayName("批内第 2 个文件不合法 → 第 1 个已落盘的文件被清理（不留垃圾文件）")
    void partialFailureCleansUpStoredFiles() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, "attachments", List.of(
                new MockMultipartFile("files", "ok.pdf", "application/pdf", PDF_BYTES),
                new MockMultipartFile("files", "bad.exe", "application/octet-stream", EXE_BYTES)),
                initiator()))
                .isInstanceOf(BizException.class);

        verify(attachmentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).as("回滚必须清掉先落盘的文件").isZero();
    }

    @Test
    @DisplayName("空 multipart → 40012（不静默成功）")
    void emptyUploadIsRejected() {
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, null, List.of(), initiator()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_POLICY_DENIED));
        assertThatThrownBy(() -> service.upload(INSTANCE_ID, null, null, initiator()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("清单：按轮次分组，出参不含 storage_path")
    void listGroupsByRoundAndHidesStoragePath() {
        Attachment round0 = storedRow("draft");
        round0.setRound(0);
        Attachment round1 = new Attachment();
        round1.setId(8L);
        round1.setInstanceId(INSTANCE_ID);
        round1.setRound(1);
        round1.setFileName("补件.pdf");
        round1.setStoragePath("2026/10/03/ffffffffffffffffffffffffffffffff.pdf");
        when(attachmentMapper.selectByInstance(INSTANCE_ID)).thenReturn(List.of(round0, round1));

        Map<String, Object> view = service.list(INSTANCE_ID);

        assertThat(view.get("total")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<Integer, List<Map<String, Object>>> rounds =
                (Map<Integer, List<Map<String, Object>>>) view.get("rounds");
        assertThat(rounds).containsOnlyKeys(0, 1);
        assertThat(rounds.get(1).get(0)).doesNotContainKey("storagePath");
        assertThat(rounds.get(1).get(0)).containsEntry("fileName", "补件.pdf");
    }

    // ================================================================ 夹具

    private static FlowInstanceRow instance(String status, String subStatus) {
        return instance(status, subStatus, "matter");
    }

    private static FlowInstanceRow instance(String status, String subStatus, String formType) {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(INSTANCE_ID);
        row.setBizNo("OA-2026-400007");
        row.setStatus(status);
        row.setSubStatus(subStatus);
        row.setFormType(formType);
        row.setInitiatorId(INITIATOR_ID);
        row.setTemplateId(1L);
        row.setTemplateVersion(1);
        row.setFormDataId(5001L);
        row.setSupplementCount(0);
        row.setCurrentNodeSeq(1);
        return row;
    }

    /** 造一条**真的落过盘**的附件行（删除用例需要物理文件真实存在）。 */
    private Attachment storedRow(String status) {
        try (InputStream in = new java.io.ByteArrayInputStream(PDF_BYTES)) {
            AttachmentStorage.StoredFile stored = storage.store("pdf", in, PDF_BYTES.length);
            Attachment row = new Attachment();
            row.setId(7L);
            row.setInstanceId(INSTANCE_ID);
            row.setRound(0);
            row.setFieldCode("attachments");
            row.setFileName("报告.pdf");
            row.setFileSize(stored.sizeBytes());
            row.setFileExt("pdf");
            row.setMimeType("application/pdf");
            row.setStoragePath(stored.relativePath());
            row.setSha256(stored.sha256());
            row.setUploaderId(INITIATOR_ID);
            return row;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static CurrentUser initiator() {
        return CurrentUser.of(INITIATOR_ID, "u208", "普通员工", "A208", 138L, 12L, Set.of("employee"),
                Set.of(DataScopeType.SELF), false);
    }

    private static CurrentUser otherUser() {
        return CurrentUser.of(OTHER_USER_ID, "u209", "内勤", "A209", 210L, 1L, Set.of("employee"),
                Set.of(DataScopeType.SELF), false);
    }

    private static CurrentUser admin() {
        return CurrentUser.of(1L, "u1", "系统管理员", "A001", 1L, 1L, Set.of("admin"),
                Set.of(DataScopeType.GROUP_ALL), true);
    }

    private static long countFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] pngBytes() {
        byte[] bytes = new byte[64];
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    /** 事项单最小 schema：{@code attachments} 收窄成 1MB / 3 个 / 2000 字符的 message。 */
    private static String matterSchemaJson() {
        return """
                {
                  "form_type": "matter",
                  "template_code": "matter",
                  "schema_version": 1,
                  "fields": [
                    {"code": "title", "label": "事项标题", "type": "text",
                     "rules": [{"type": "maxLength", "value": 60}]},
                    {"code": "attachments", "label": "附件", "type": "files",
                     "rules": [{"type": "filePolicy", "maxSizeMb": 1, "maxCount": 3,
                                "message": "本单最多 3 个附件"}]}
                  ]
                }
                """;
    }

    /**
     * 合同单最小 schema：{@code attachments}（必填）与 {@code counterparty_docs}（非必填、同为
     * {@code type=files}）—— 待补件窗口必须按**字段类型**同时放行两者（B 项裁定）。
     */
    private static String contractSchemaJson() {
        return """
                {
                  "form_type": "contract",
                  "template_code": "contract",
                  "schema_version": 1,
                  "fields": [
                    {"code": "title", "label": "合同名称", "type": "text",
                     "rules": [{"type": "maxLength", "value": 80}]},
                    {"code": "amount", "label": "合同金额", "type": "amount", "required": true,
                     "rules": [{"type": "amountRange", "min": "0.01"}]},
                    {"code": "attachments", "label": "合同文本附件", "type": "files", "required": true,
                     "rules": [{"type": "filePolicy", "maxSizeMb": 50, "maxCount": 20}]},
                    {"code": "counterparty_docs", "label": "对方资质附件", "type": "files", "required": false,
                     "rules": [{"type": "filePolicy", "maxSizeMb": 50, "maxCount": 20}]}
                  ]
                }
                """;
    }
}
