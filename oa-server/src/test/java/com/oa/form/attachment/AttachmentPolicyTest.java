package com.oa.form.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.form.attachment.domain.AttachmentPolicy;
import com.oa.form.attachment.domain.AttachmentPolicy.Bounds;
import com.oa.form.attachment.domain.AttachmentPolicy.ContentKind;
import com.oa.form.attachment.domain.AttachmentPolicy.Report;
import com.oa.form.template.schema.FormFieldDef;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.7 附件上传规则</b>的穷举单测（纯函数，无 DB / 无 Spring 容器）。
 *
 * <p>证明的对象是「服务端是边界」：三档限额（单文件 / 单字段 / 单据合计 + 单次请求）、
 * 格式**双校验**（黑名单优先于白名单）、内容嗅探与扩展名一致性、文件名安全。
 *
 * <p>真源出处逐条见 {@link AttachmentPolicy} 的类注释与各方法注释。
 */
class AttachmentPolicyTest {

    private static final byte[] PDF_HEAD = "%PDF-1.7\n%âãÏÓ".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PNG_HEAD = new byte[] {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG_HEAD = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] ZIP_HEAD = new byte[] {'P', 'K', 0x03, 0x04, 0x14, 0, 0, 0};
    private static final byte[] OLE2_HEAD = new byte[] {
        (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0, 0, 0};
    private static final byte[] RAR_HEAD = new byte[] {'R', 'a', 'r', '!', 0x1A, 0x07, 0x00};
    private static final byte[] SEVEN_ZIP_HEAD = new byte[] {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] HEIC_HEAD = new byte[] {
        0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
    private static final byte[] EXE_HEAD = new byte[] {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0, 0, 0};
    private static final byte[] ELF_HEAD = new byte[] {0x7F, 'E', 'L', 'F', 2, 1, 1, 0};
    private static final byte[] SHELL_HEAD = "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8);

    private static Bounds defaults() {
        return new Bounds(AttachmentPolicy.GLOBAL_MAX_SIZE_MB, AttachmentPolicy.GLOBAL_MAX_PER_UPLOAD,
                AttachmentPolicy.GLOBAL_MAX_PER_UPLOAD, AttachmentPolicy.GLOBAL_MAX_PER_INSTANCE,
                AttachmentPolicy.ALLOWED_EXT, AttachmentPolicy.DENIED_EXT, null);
    }

    // ================================================================ 档位来源（真源）

    @Test
    @DisplayName("全局档位逐字对齐 doc/forms.md §1.4：50MB / 20 个 / 50 个 / 15 种 / 9 种")
    void globalBoundsMatchSourceOfTruth() {
        assertThat(AttachmentPolicy.GLOBAL_MAX_SIZE_MB).isEqualTo(50);
        assertThat(AttachmentPolicy.GLOBAL_MAX_PER_UPLOAD).isEqualTo(20);
        assertThat(AttachmentPolicy.GLOBAL_MAX_PER_INSTANCE).isEqualTo(50);
        assertThat(AttachmentPolicy.ALLOWED_EXT).hasSize(15)
                .containsExactlyInAnyOrder("pdf", "doc", "docx", "wps", "xls", "xlsx", "ppt", "pptx",
                        "jpg", "jpeg", "png", "heic", "zip", "rar", "7z");
        assertThat(AttachmentPolicy.DENIED_EXT).hasSize(9)
                .containsExactlyInAnyOrder("exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr");
    }

    @Test
    @DisplayName("模板 filePolicy 可收窄档位；非法（≤0）配置按全局缺省兜底，绝不放行成无上限")
    void templateFilePolicyNarrowsBounds() throws Exception {
        FormFieldDef narrowed = field("""
                {"code":"attachments","label":"附件","type":"files","rules":[
                  {"type":"filePolicy","maxSizeMb":5,"maxCount":3,
                   "allowExt":["pdf","png"],"denyExt":["exe"],"message":"本单附件规格不符"}]}
                """);
        Bounds bounds = AttachmentPolicy.boundsOf(narrowed);
        assertThat(bounds.maxSizeMb()).isEqualTo(5);
        assertThat(bounds.maxCount()).isEqualTo(3);
        assertThat(bounds.allowExt()).containsExactlyInAnyOrder("pdf", "png");
        assertThat(bounds.message()).isEqualTo("本单附件规格不符");
        // 单次上传与单据合计是 §1.4 的**全局**档位，模板不能放宽
        assertThat(bounds.maxPerUpload()).isEqualTo(20);
        assertThat(bounds.maxPerInstance()).isEqualTo(50);

        FormFieldDef broken = field("""
                {"code":"attachments","label":"附件","type":"files","rules":[
                  {"type":"filePolicy","maxSizeMb":0,"maxCount":-1}]}
                """);
        Bounds fallback = AttachmentPolicy.boundsOf(broken);
        assertThat(fallback.maxSizeMb()).isEqualTo(AttachmentPolicy.GLOBAL_MAX_SIZE_MB);
        assertThat(fallback.maxCount()).isEqualTo(AttachmentPolicy.GLOBAL_MAX_PER_UPLOAD);
    }

    // ================================================================ 格式双校验

    @Test
    @DisplayName("黑名单优先：.exe/.bat/.dll 即便扩展名不在白名单也要报「禁止格式」（doc/enums.md §12.2）")
    void deniedExtensionWinsOverWhitelist() {
        Bounds bounds = defaults();
        assertThat(AttachmentPolicy.check(bounds, "virus.exe", "application/octet-stream",
                1024, EXE_HEAD).message()).contains("不允许上传 exe 格式");
        assertThat(AttachmentPolicy.check(bounds, "tool.bat", null, 10, SHELL_HEAD).message())
                .contains("不允许上传 bat 格式");
        assertThat(AttachmentPolicy.check(bounds, "run.ps1", null, 10, SHELL_HEAD).message())
                .contains("不允许上传 ps1 格式");
        assertThat(AttachmentPolicy.check(bounds, "lib.dll", null, 10, EXE_HEAD).message())
                .contains("不允许上传 dll 格式");
        // 9 种之外（.sh / .txt）走**白名单外**分支：仍是拒绝，只是文案不同
        assertThat(AttachmentPolicy.check(bounds, "run.sh", null, 10, SHELL_HEAD).message())
                .contains("仅支持");
    }

    @Test
    @DisplayName("白名单外被拒：.txt/.html 不在 15 种之内（doc/forms.md §1.4）")
    void extensionOutsideWhitelistIsRejected() {
        Report report = AttachmentPolicy.check(defaults(), "note.txt", "text/plain", 10,
                "hello".getBytes(StandardCharsets.UTF_8));
        assertThat(report.ok()).isFalse();
        assertThat(report.message()).contains("仅支持").contains("pdf");
    }

    @Test
    @DisplayName("危险 MIME 命中即拒（客户端声明也不放过）")
    void deniedDeclaredMimeIsRejected() {
        // 扩展名合法但 MIME 直接声明成可执行文件 → 拒
        Report report = AttachmentPolicy.check(defaults(), "a.pdf", "application/x-msdownload", 10, PDF_HEAD);
        assertThat(report.ok()).isFalse();
        assertThat(report.message()).contains("MIME=application/x-msdownload");
    }

    @Test
    @DisplayName("15 种允许格式的**正例**逐项通过（含 V0.4 放行的 heic/wps）")
    void allowedFormatsPassWithMatchingContent() {
        Bounds bounds = defaults();
        assertThat(AttachmentPolicy.check(bounds, "a.pdf", "application/pdf", 1024, PDF_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.jpg", "image/jpeg", 1024, JPEG_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.jpeg", "image/jpeg", 1024, JPEG_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.png", "image/png", 1024, PNG_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.heic", "image/heic", 1024, HEIC_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.zip", "application/zip", 1024, ZIP_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.docx", null, 1024, ZIP_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.xlsx", null, 1024, ZIP_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.pptx", null, 1024, ZIP_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.doc", null, 1024, OLE2_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.xls", null, 1024, OLE2_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.ppt", null, 1024, OLE2_HEAD).ok()).isTrue();
        // wps：OLE2（旧）与 ZIP（新）两种形态都放行
        assertThat(AttachmentPolicy.check(bounds, "a.wps", null, 1024, OLE2_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.wps", null, 1024, ZIP_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.rar", null, 1024, RAR_HEAD).ok()).isTrue();
        assertThat(AttachmentPolicy.check(bounds, "a.7z", null, 1024, SEVEN_ZIP_HEAD).ok()).isTrue();
    }

    // ================================================================ 内容嗅探

    @Test
    @DisplayName("扩展名与内容不符被拒：.png 里塞 PE 可执行内容 / .pdf 里塞 ZIP")
    void extensionContentMismatchIsRejected() {
        Report exeInPng = AttachmentPolicy.check(defaults(), "photo.png", "image/png", 4096, EXE_HEAD);
        assertThat(exeInPng.ok()).isFalse();
        assertThat(exeInPng.message()).contains("可执行文件");

        Report zipInPdf = AttachmentPolicy.check(defaults(), "doc.pdf", "application/pdf", 4096, ZIP_HEAD);
        assertThat(zipInPdf.ok()).isFalse();
        assertThat(zipInPdf.message()).contains("内容与扩展名 .pdf 不符").contains("ZIP");

        // 反向：真正可渲染的 png 头塞进 .pdf 同样拒
        Report pngInPdf = AttachmentPolicy.check(defaults(), "doc.pdf", "application/pdf", 4096, PNG_HEAD);
        assertThat(pngInPdf.ok()).isFalse();
        assertThat(pngInPdf.message()).contains("PNG");
    }

    @Test
    @DisplayName("脚本内容（#!）与 ELF 在**合法扩展名**下一律拒；非法扩展名先被白名单拦（更早的确定性结论）")
    void scriptAndElfAreAlwaysRejected() {
        // .txt 不在白名单 → 先报「仅支持」（格式判定先于内容判定）
        assertThat(AttachmentPolicy.check(defaults(), "a.txt", null, 10, SHELL_HEAD).message())
                .contains("仅支持");
        // .pdf 在白名单内但内容是脚本 → 拒
        assertThat(AttachmentPolicy.check(defaults(), "a.pdf", "application/pdf", 10, SHELL_HEAD).message())
                .contains("脚本");
        // .7z 在白名单内但内容是 ELF → 按可执行文件拒
        assertThat(AttachmentPolicy.check(defaults(), "a.7z", null, 10, ELF_HEAD).message())
                .contains("可执行文件");
        // .zip 内容伪装成合法 PDF 头 → 按「与扩展名不符」拒
        assertThat(AttachmentPolicy.check(defaults(), "a.zip", null, 10, PDF_HEAD).message())
                .contains("内容与扩展名 .zip 不符");
    }

    @Test
    @DisplayName("空文件 / 头字节不足 → 按「与扩展名不符」拒（不把截断文件当合法附件）")
    void emptyOrTruncatedContentIsRejected() {
        Report empty = AttachmentPolicy.check(defaults(), "a.pdf", "application/pdf", 0, new byte[0]);
        assertThat(empty.ok()).isFalse();
        assertThat(AttachmentPolicy.sniff(new byte[0])).isEqualTo(ContentKind.UNKNOWN);
        assertThat(AttachmentPolicy.check(defaults(), "a.pdf", "application/pdf", 4, new byte[] {0x25})
                .ok()).isFalse();
    }

    // ================================================================ 三档限额 + 单次

    @Test
    @DisplayName("第一档·单文件大小：50MB 恰好通过，50MB+1 字节被拒，文案带「50MB」（AC-45）")
    void singleFileSizeLimit() {
        long limit = 50L * 1024 * 1024;
        assertThat(AttachmentPolicy.check(defaults(), "a.pdf", "application/pdf", limit, PDF_HEAD).ok()).isTrue();
        Report over = AttachmentPolicy.check(defaults(), "a.pdf", "application/pdf", limit + 1, PDF_HEAD);
        assertThat(over.ok()).isFalse();
        assertThat(over.message()).contains("单个文件不超过 50MB");
    }

    @Test
    @DisplayName("第二档·单字段数量：模板 maxCount 生效；缺省 20（doc/templates.md §2.3）")
    void perFieldCountLimit() {
        Bounds bounds = defaults();
        assertThat(AttachmentPolicy.checkFieldCount(bounds, 19, 1).ok()).isTrue();
        Report over = AttachmentPolicy.checkFieldCount(bounds, 20, 1);
        assertThat(over.ok()).isFalse();
        assertThat(over.message()).contains("不超过 20 个");

        Bounds narrow = new Bounds(50, 3, 20, 50, AttachmentPolicy.ALLOWED_EXT,
                AttachmentPolicy.DENIED_EXT, "本单最多 3 个附件");
        assertThat(AttachmentPolicy.checkFieldCount(narrow, 2, 1).ok()).isTrue();
        assertThat(AttachmentPolicy.checkFieldCount(narrow, 3, 1).message()).isEqualTo("本单最多 3 个附件");
    }

    @Test
    @DisplayName("第三档·单据合计：含补件 ≤50（AC-45 / TC-FORM-026）")
    void perInstanceCountLimit() {
        Bounds bounds = defaults();
        assertThat(AttachmentPolicy.checkInstanceCount(bounds, 45, 5).ok()).isTrue();
        Report over = AttachmentPolicy.checkInstanceCount(bounds, 50, 1);
        assertThat(over.ok()).isFalse();
        assertThat(over.message()).contains("单张单据附件总数不超过 50 个").contains("含补件");
    }

    @Test
    @DisplayName("单次上传数量：≤20（§1.4；TC-FORM-023③ 第 21 个被拒）")
    void perUploadCountLimit() {
        Bounds bounds = defaults();
        assertThat(AttachmentPolicy.checkPerUpload(bounds, 20).ok()).isTrue();
        Report over = AttachmentPolicy.checkPerUpload(bounds, 21);
        assertThat(over.ok()).isFalse();
        assertThat(over.message()).contains("单次上传不超过 20 个").contains("21");
        assertThat(AttachmentPolicy.checkPerUpload(bounds, 0).ok()).isFalse();
    }

    // ================================================================ 文件名安全

    @Test
    @DisplayName("文件名安全：剥离路径分隔符与 ..（两种分隔符）、剔除控制字符、限长 255")
    void displayNameIsSanitized() {
        assertThat(AttachmentPolicy.safeDisplayName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(AttachmentPolicy.safeDisplayName("..\\..\\windows\\system32\\a.exe")).isEqualTo("a.exe");
        assertThat(AttachmentPolicy.safeDisplayName("C:\\Users\\me\\合同.pdf")).isEqualTo("合同.pdf");
        assertThat(AttachmentPolicy.safeDisplayName("a\u0000b\r\nc.pdf")).isEqualTo("abc.pdf");
        assertThat(AttachmentPolicy.safeDisplayName("..")).isEqualTo(AttachmentPolicy.FALLBACK_NAME);
        assertThat(AttachmentPolicy.safeDisplayName("")).isEqualTo(AttachmentPolicy.FALLBACK_NAME);
        assertThat(AttachmentPolicy.safeDisplayName("  .")).isEqualTo(AttachmentPolicy.FALLBACK_NAME);
        assertThat(AttachmentPolicy.safeDisplayName("/")).isEqualTo(AttachmentPolicy.FALLBACK_NAME);
        assertThat(AttachmentPolicy.safeDisplayName(".hidden.pdf")).isEqualTo("hidden.pdf");

        String longName = "很长的名字".repeat(100) + ".pdf";
        String safe = AttachmentPolicy.safeDisplayName(longName);
        assertThat(safe.length()).isLessThanOrEqualTo(AttachmentPolicy.MAX_DISPLAY_NAME_LENGTH);
        assertThat(safe).endsWith(".pdf");
    }

    @Test
    @DisplayName("存储名与展示名分离：随机、只含 [0-9a-f]{32} 与已校验扩展名，绝不含原名片段")
    void storageNameIsRandomAndDecoupled() {
        String first = AttachmentPolicy.storageName("pdf");
        String second = AttachmentPolicy.storageName("pdf");
        assertThat(first).matches("[0-9a-f]{32}\\.pdf");
        assertThat(second).matches("[0-9a-f]{32}\\.pdf");
        assertThat(first).isNotEqualTo(second);
        assertThat(AttachmentPolicy.storageName("")).matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("扩展名解析：取最后一段、忽略路径、大小写归一、无扩展名返回空")
    void extensionParsing() {
        assertThat(AttachmentPolicy.extensionOf("A.PDF")).isEqualTo("pdf");
        assertThat(AttachmentPolicy.extensionOf("/a/b/c.docx")).isEqualTo("docx");
        assertThat(AttachmentPolicy.extensionOf("C:\\a\\b.tar.gz")).isEqualTo("gz");
        assertThat(AttachmentPolicy.extensionOf("noext")).isEmpty();
        assertThat(AttachmentPolicy.extensionOf(".hidden")).isEmpty();
        assertThat(AttachmentPolicy.extensionOf("trailing.")).isEmpty();
        assertThat(AttachmentPolicy.extensionOf(null)).isEmpty();
    }

    private static FormFieldDef field(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return FormFieldDef.from(mapper.readTree(json));
    }
}
