package com.oa.form.attachment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.form.template.schema.FormFieldDef;
import com.oa.form.template.validate.FormPayloadValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>附件上传规则的唯一判定源（纯函数）</b> —— 阶段 2b.7。
 *
 * <h2>真源（逐条出处）</h2>
 * <ul>
 *   <li>{@code doc/forms.md} §1.4 附件通用限制表：单文件 ≤ 50 MB / 单次上传 ≤ 20 个 /
 *       单张单据附件总数 ≤ 50 个（含补件）/ 允许格式 15 种 / 禁止格式 9 种
 *       「上传即拒绝，服务端校验扩展名与 MIME 双重判断」/「私有化本地存储，不存公网；
 *       下载必须经鉴权接口，禁止直链」/ 补件轮次 {@code 0..3}；</li>
 *   <li>{@code doc/enums.md} §12.1（15 种允许格式，逐项）、§12.2（9 种禁止格式）、
 *       §12.2 末注「校验必须**扩展名 + MIME 双重判断**，二者任一命中黑名单即拒绝」；</li>
 *   <li>{@code doc/templates.md} §2.3 {@code filePolicy} 行：{@code maxSizeMb / maxCount /
 *       minCount / allowExt / denyExt} + 「所有规则项都支持 {@code message}（违反时的中文提示文案）」，
 *       「缺省取全局规则（50MB、20 个、15 种格式）」；</li>
 *   <li>{@code doc/prd-0.1.md} AC-45：「上传 {@code exe}、超过 50MB、或第 21 个文件被拒；
 *       直接访问存储路径返回 403；经鉴权接口下载成功且写入日志（REQ-FORM-002）」。</li>
 * </ul>
 *
 * <h2>本类相对真源的两处**加强**（只收紧、不放宽）</h2>
 * <ol>
 *   <li><b>内容嗅探（魔数）</b>：真源只写「扩展名 + MIME 双重判断」，而 MIME 由客户端声明、
 *       完全可伪造，仅凭它 + 扩展名等于「只信客户端」。因此本类在两者之外**再判文件头**，
 *       并要求「嗅探出的家族 ∈ 该扩展名允许的家族」（例：{@code .png} 里塞 PE 可执行内容 → 拒绝）。
 *       要求来自本工作包的任务口径；真源未写 → 已列入待决策。</li>
 *   <li><b>文件名安全</b>：剥离路径分隔符 / {@code ..} / 控制字符并限制长度；
 *       真源只写「禁止直链」，未写文件名处理 → 已列入待决策。</li>
 * </ol>
 *
 * <h2>为什么是纯函数</h2>
 * <p>不依赖 Spring / DB / 文件系统，因此可穷举单测（{@code AttachmentPolicyTest}）。
 * 服务端是边界：{@code AttachmentService} 必须调用本类，前端置灰不算数
 * （{@code doc/forms.md} §1.2 末段「服务端必须按状态白名单校验可写字段，不能仅依赖前端置灰」同源）。
 */
public final class AttachmentPolicy {

    // ================================================================ 全局档位（缺省值）

    /** 单文件大小上限（MB）——{@code doc/forms.md} §1.4「单文件大小 | ≤ 50 MB」。 */
    public static final int GLOBAL_MAX_SIZE_MB = FormPayloadValidator.ATTACHMENT_MAX_SIZE_MB;

    /** 单次上传数量上限——{@code doc/forms.md} §1.4「单次上传数量 | ≤ 20 个」。 */
    public static final int GLOBAL_MAX_PER_UPLOAD = FormPayloadValidator.ATTACHMENT_MAX_PER_UPLOAD;

    /** 单张单据附件总数上限（含补件）——{@code doc/forms.md} §1.4「≤ 50 个（含补件）」。 */
    public static final int GLOBAL_MAX_PER_INSTANCE = FormPayloadValidator.ATTACHMENT_MAX_PER_INSTANCE;

    /** 允许格式 15 种（{@code doc/enums.md} §12.1；与 2b.1 的校验器共用同一集合，禁止各自维护）。 */
    public static final Set<String> ALLOWED_EXT = FormPayloadValidator.ATTACHMENT_ALLOWED_EXT;

    /** 禁止格式 9 种（{@code doc/enums.md} §12.2）。 */
    public static final Set<String> DENIED_EXT = FormPayloadValidator.ATTACHMENT_DENIED_EXT;

    /** 展示名长度上限（{@code flow_attachment.file_name} 是 {@code VARCHAR(255)}）。 */
    public static final int MAX_DISPLAY_NAME_LENGTH = 255;

    /** 展示名过长且无扩展名时的兜底名。 */
    public static final String FALLBACK_NAME = "unnamed";

    // ================================================================ 内容家族

    /**
     * 文件**内容**家族（由魔数嗅探得出，不依赖文件名与客户端 MIME）。
     *
     * <p>{@link #EXECUTABLE} / {@link #SCRIPT} 是**任何扩展名都不可放行**的家族
     * （{@code doc/enums.md} §12.2「上传即拒绝」）。
     */
    public enum ContentKind {

        /** {@code %PDF-}。 */
        PDF("PDF"),
        /** {@code FF D8 FF}。 */
        JPEG("JPEG"),
        /** {@code 89 50 4E 47 0D 0A 1A 0A}。 */
        PNG("PNG"),
        /** ISO-BMFF {@code ftyp} + {@code heic/heix/mif1/msf1} 品牌。 */
        HEIC("HEIC"),
        /** {@code PK\x03\x04}（ZIP 容器；OOXML 的 docx/xlsx/pptx 同族）。 */
        ZIP("ZIP"),
        /** OLE2 复合文档 {@code D0 CF 11 E0 A1 B1 1A E1}（doc/xls/ppt/wps）。 */
        OLE2("OLE2"),
        /** {@code Rar!\x1A\x07}。 */
        RAR("RAR"),
        /** {@code 37 7A BC AF 27 1C}。 */
        SEVEN_ZIP("7Z"),
        /** PE/COFF（{@code MZ}）或 ELF（{@code \x7FELF}）。 */
        EXECUTABLE("可执行文件"),
        /** 脚本（{@code #!} shebang）。 */
        SCRIPT("脚本"),
        /** 无法识别 / 头字节不足。 */
        UNKNOWN("未知");

        private final String label;

        ContentKind(String label) {
            this.label = label;
        }

        /** 中文标签（错误文案用）。 */
        public String label() {
            return label;
        }
    }

    /**
     * 扩展名 → 允许的内容家族（**扩展名与内容一致性**的判据）。
     *
     * <p>逐条取自 {@code doc/enums.md} §12.1 的「中文名」列：
     * {@code doc}/{@code xls}/{@code ppt} 是 Office 97-2003 → OLE2；
     * {@code docx}/{@code xlsx}/{@code pptx} 是 OOXML → ZIP；
     * {@code wps} 是 WPS 文档，实际有 OLE2（旧版）与 ZIP（新版，基于 OOXML）两种形态，故放行两族；
     * {@code zip} 与 OOXML 同为 ZIP 容器，无法在不解压的前提下区分 → 同族放行。
     */
    private static final Map<String, Set<ContentKind>> EXPECTED_KINDS = expectedKinds();

    private static Map<String, Set<ContentKind>> expectedKinds() {
        Map<String, Set<ContentKind>> map = new LinkedHashMap<>();
        map.put("pdf", Set.of(ContentKind.PDF));
        map.put("jpg", Set.of(ContentKind.JPEG));
        map.put("jpeg", Set.of(ContentKind.JPEG));
        map.put("png", Set.of(ContentKind.PNG));
        map.put("heic", Set.of(ContentKind.HEIC));
        map.put("zip", Set.of(ContentKind.ZIP));
        map.put("docx", Set.of(ContentKind.ZIP));
        map.put("xlsx", Set.of(ContentKind.ZIP));
        map.put("pptx", Set.of(ContentKind.ZIP));
        map.put("doc", Set.of(ContentKind.OLE2));
        map.put("xls", Set.of(ContentKind.OLE2));
        map.put("ppt", Set.of(ContentKind.OLE2));
        map.put("wps", Set.of(ContentKind.OLE2, ContentKind.ZIP));
        map.put("rar", Set.of(ContentKind.RAR));
        map.put("7z", Set.of(ContentKind.SEVEN_ZIP));
        return Map.copyOf(map);
    }

    /**
     * 客户端声明 MIME 的**黑名单**（命中即拒，不看扩展名）。
     *
     * <p>真源只说「扩展名与 MIME 双重判断」而没有给 MIME 清单，这里按
     * {@code doc/enums.md} §12.2 的 9 种禁止格式所能对应的**标准 MIME** 收敛；
     * 未列出的 MIME 不因此放行（内容嗅探才是权威判据）。
     */
    private static final Set<String> DENIED_MIME = Set.of(
            "application/x-msdownload", "application/x-dosexec", "application/x-msdos-program",
            "application/x-executable", "application/vnd.microsoft.portable-executable",
            "application/x-sh", "application/x-shellscript", "text/x-shellscript",
            "application/x-bat", "application/x-msi", "application/x-msinstaller",
            "application/javascript", "text/javascript", "application/x-javascript",
            "application/x-vbs", "text/vbs", "application/x-powershell", "text/x-powershell");

    private AttachmentPolicy() {
    }

    // ================================================================ 档位解析

    /**
     * 某附件字段的**生效档位**（模板 {@code filePolicy} 可收窄，未声明取全局缺省）。
     *
     * @param field 字段定义（{@code type} 为 {@code file} / {@code files}）
     */
    public static Bounds boundsOf(FormFieldDef field) {
        JsonNode rule = field == null ? null : field.rule("filePolicy");
        int maxSizeMb = rule != null && rule.path("maxSizeMb").isNumber()
                ? rule.path("maxSizeMb").asInt() : GLOBAL_MAX_SIZE_MB;
        int maxCount = rule != null && rule.path("maxCount").isNumber()
                ? rule.path("maxCount").asInt() : GLOBAL_MAX_PER_UPLOAD;
        Set<String> allow = rule != null && rule.has("allowExt")
                ? lower(rule.get("allowExt")) : ALLOWED_EXT;
        Set<String> deny = rule != null && rule.has("denyExt")
                ? lower(rule.get("denyExt")) : DENIED_EXT;
        String message = field == null ? null : field.ruleMessage("filePolicy");
        // 模板把上限配成非正数是配置错误：按全局缺省兜底，绝不放行成"无上限"
        if (maxSizeMb <= 0) {
            maxSizeMb = GLOBAL_MAX_SIZE_MB;
        }
        if (maxCount <= 0) {
            maxCount = GLOBAL_MAX_PER_UPLOAD;
        }
        return new Bounds(maxSizeMb, maxCount, GLOBAL_MAX_PER_UPLOAD, GLOBAL_MAX_PER_INSTANCE,
                allow, deny, message);
    }

    /**
     * 生效档位（**三档限额 + 双清单**）。
     *
     * @param maxSizeMb       单文件大小上限（MB）
     * @param maxCount        单字段附件数量上限（模板 {@code filePolicy.maxCount}）
     * @param maxPerUpload    单次上传数量上限（§1.4：≤20）
     * @param maxPerInstance  单张单据附件总数上限（§1.4：≤50，含补件）
     * @param allowExt        允许扩展名白名单
     * @param denyExt         禁止扩展名黑名单（**优先于白名单**）
     * @param message         模板 {@code filePolicy.message}（违反时的中文提示文案，可为 {@code null}）
     */
    public record Bounds(
            int maxSizeMb,
            int maxCount,
            int maxPerUpload,
            int maxPerInstance,
            Set<String> allowExt,
            Set<String> denyExt,
            String message
    ) {
        public Bounds {
            allowExt = allowExt == null ? Set.of() : Set.copyOf(allowExt);
            denyExt = denyExt == null ? Set.of() : Set.copyOf(denyExt);
        }

        /** 单文件字节上限。 */
        public long maxSizeBytes() {
            return maxSizeMb * 1024L * 1024L;
        }
    }

    // ================================================================ 逐项校验

    /**
     * 校验一个待落库的附件。
     *
     * <p>顺序固定为 <b>文件名 → 扩展名黑名单 → 白名单 → 客户端 MIME → 内容嗅探 → 大小</b>：
     * 黑名单**先于**白名单（{@code doc/enums.md} §12.2 末注「二者任一命中黑名单即拒绝」），
     * 且尺寸判定放在最后 —— 先给出「格式不允许」这种确定性更高的结论。
     *
     * @param bounds        生效档位
     * @param rawFileName   客户端原始文件名（**不可信**）
     * @param declaredMime  客户端声明 MIME（**不可信**，可为 {@code null}）
     * @param sizeBytes     实际字节数（由服务端从 MultipartFile 读取，不取客户端声明）
     * @param head          文件头若干字节（用于魔数嗅探；建议 ≥ 32 字节）
     * @return 校验结论（不通过时 {@link Report#message()} 自带档位与实测值）
     */
    public static Report check(Bounds bounds, String rawFileName, String declaredMime,
                               long sizeBytes, byte[] head) {
        if (bounds == null) {
            return Report.failed("附件规则缺失，已按 fail-closed 拒绝");
        }
        String ext = extensionOf(rawFileName);
        if (ext.isEmpty()) {
            return Report.failed("文件名缺少扩展名，无法判定格式；允许格式："
                    + String.join("/", sorted(bounds.allowExt())));
        }
        // ① 黑名单优先（doc/enums.md §12.2「上传即拒绝」）
        if (bounds.denyExt().contains(ext)) {
            return Report.failed(String.format("不允许上传 %s 格式的文件（上传即拒绝；doc/enums.md §12.2）", ext));
        }
        // ② 白名单（15 种）
        if (!bounds.allowExt().contains(ext)) {
            return Report.failed(String.format("仅支持 %s（doc/forms.md §1.4）",
                    String.join("/", sorted(bounds.allowExt()))));
        }
        // ③ 客户端声明的 MIME（可伪造，只作**加严**：命中危险 MIME 即拒）
        if (declaredMime != null && DENIED_MIME.contains(declaredMime.trim().toLowerCase(Locale.ROOT))) {
            return Report.failed(String.format("不允许上传该类型的文件（MIME=%s；doc/enums.md §12.2）",
                    declaredMime));
        }
        // ④ 内容嗅探：扩展名与内容必须一致（加强项，见类注释）
        ContentKind kind = sniff(head);
        if (kind == ContentKind.EXECUTABLE) {
            return Report.failed("文件内容是可执行文件（PE/ELF），无论扩展名一律拒绝（doc/enums.md §12.2）");
        }
        if (kind == ContentKind.SCRIPT) {
            return Report.failed("文件内容是脚本（#!），无论扩展名一律拒绝（doc/enums.md §12.2）");
        }
        Set<ContentKind> expected = EXPECTED_KINDS.get(ext);
        if (expected != null && !expected.contains(kind)) {
            return Report.failed(String.format(
                    "文件内容与扩展名 .%s 不符（嗅探到 %s，期望 %s）；服务端不只信客户端声明的 MIME 与文件名",
                    ext, kind.label(), expected.stream().map(ContentKind::label).sorted()
                            .reduce((a, b) -> a + "/" + b).orElse("—")));
        }
        // ⑤ 大小（**三档**里的第一档）
        if (sizeBytes > bounds.maxSizeBytes()) {
            return Report.failed(String.format("单个文件不超过 %dMB（doc/forms.md §1.4）", bounds.maxSizeMb()));
        }
        if (sizeBytes <= 0) {
            return Report.failed("空文件不予接收（0 字节）");
        }
        return Report.passed();
    }

    /** 校验单次上传数量（§1.4「单次上传数量 | ≤ 20 个」）。 */
    public static Report checkPerUpload(Bounds bounds, int incoming) {
        if (incoming <= 0) {
            return Report.failed("未提供任何文件");
        }
        if (incoming > bounds.maxPerUpload()) {
            return Report.failed(String.format("单次上传不超过 %d 个（doc/forms.md §1.4；本次 %d 个）",
                    bounds.maxPerUpload(), incoming));
        }
        return Report.passed();
    }

    /** 校验单字段累计数量（模板 {@code filePolicy.maxCount}，缺省 20）。 */
    public static Report checkFieldCount(Bounds bounds, int existing, int incoming) {
        int total = existing + incoming;
        if (total > bounds.maxCount()) {
            String custom = bounds.message();
            return Report.failed(custom != null && !custom.isBlank() ? custom
                    : String.format("「附件」单次上传不超过 %d 个（本次累计 %d 个；doc/templates.md §2.3 filePolicy.maxCount）",
                            bounds.maxCount(), total));
        }
        return Report.passed();
    }

    /** 校验单张单据附件总数（§1.4「≤ 50 个（含补件）」，AC-45 / TC-FORM-026）。 */
    public static Report checkInstanceCount(Bounds bounds, int existing, int incoming) {
        int total = existing + incoming;
        if (total > bounds.maxPerInstance()) {
            return Report.failed(String.format("单张单据附件总数不超过 %d 个（含补件；本次累计 %d 个）",
                    bounds.maxPerInstance(), total));
        }
        return Report.passed();
    }

    /** 校验结论。 */
    public record Report(boolean ok, String message) {

        static Report passed() {
            return new Report(true, null);
        }

        static Report failed(String message) {
            return new Report(false, message);
        }
    }

    // ================================================================ 内容嗅探

    /**
     * 魔数嗅探（**只看内容，不看文件名与 MIME**）。
     *
     * <p>头字节不足时返回 {@link ContentKind#UNKNOWN} —— 调用方据此按「与扩展名不符」拒绝
     * （0 字节与截断文件都不应被当作合法附件）。
     */
    public static ContentKind sniff(byte[] head) {
        if (head == null || head.length < 2) {
            return ContentKind.UNKNOWN;
        }
        if (startsWith(head, new int[] {0x25, 0x50, 0x44, 0x46})) {
            return ContentKind.PDF;
        }
        if (startsWith(head, new int[] {0xFF, 0xD8, 0xFF})) {
            return ContentKind.JPEG;
        }
        if (startsWith(head, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ContentKind.PNG;
        }
        if (startsWith(head, new int[] {'P', 'K', 0x03, 0x04})
                || startsWith(head, new int[] {'P', 'K', 0x05, 0x06})) {
            return ContentKind.ZIP;
        }
        if (startsWith(head, new int[] {0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1})) {
            return ContentKind.OLE2;
        }
        if (startsWith(head, new int[] {'R', 'a', 'r', '!', 0x1A, 0x07})) {
            return ContentKind.RAR;
        }
        if (startsWith(head, new int[] {0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C})) {
            return ContentKind.SEVEN_ZIP;
        }
        if (startsWith(head, new int[] {'M', 'Z'}) || startsWith(head, new int[] {0x7F, 'E', 'L', 'F'})) {
            return ContentKind.EXECUTABLE;
        }
        if (startsWith(head, new int[] {'#', '!'})) {
            return ContentKind.SCRIPT;
        }
        if (lengthAtLeast(head, 12) && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            String brand = new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                    .toLowerCase(Locale.ROOT);
            if (Set.of("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "heis").contains(brand)) {
                return ContentKind.HEIC;
            }
        }
        return ContentKind.UNKNOWN;
    }

    private static boolean startsWith(byte[] data, int[] magic) {
        if (!lengthAtLeast(data, magic.length)) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((data[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean lengthAtLeast(byte[] data, int length) {
        return data != null && data.length >= length;
    }

    // ================================================================ 文件名安全

    /**
     * <b>展示名</b>安全化：去掉目录部分（{@code /} 与 {@code \} 都算）、{@code ..}、控制字符，
     * 并限制到 {@value #MAX_DISPLAY_NAME_LENGTH} 字符（{@code flow_attachment.file_name} 是
     * {@code VARCHAR(255)}，按**字符**计）。
     *
     * <p>展示名**只用于显示与下载时的 {@code Content-Disposition}**，
     * 绝不参与服务器路径拼接（见 {@link #storageName(String)}）。
     */
    public static String safeDisplayName(String rawName) {
        String name = rawName == null ? "" : rawName;
        // ① 两种分隔符都当路径分隔符（Windows 客户端可能传 "C:\a\b.pdf" 或 "..\..\a.pdf"）
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        // ② 控制字符（含 NUL / 换行 / 制表）一律剔除：它们会被写进响应头，是响应头注入的载体
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch >= 0x20 && ch != 0x7F) {
                builder.append(ch);
            }
        }
        name = builder.toString().trim();
        // ③ 纯 ".." / "." / 空 → 兜底；同时剔除任何 ".." 片段（防御纵深，路径拼接另有一道闸）
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return FALLBACK_NAME;
        }
        name = name.replace("..", "");
        // ④ 前导点会让文件在 Unix 上变成隐藏文件，也不符合展示预期
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return FALLBACK_NAME;
        }
        // ⑤ 长度：保留扩展名，截断中段
        if (name.length() > MAX_DISPLAY_NAME_LENGTH) {
            String ext = extensionOf(name);
            String suffix = ext.isEmpty() ? "" : "." + ext;
            int keep = MAX_DISPLAY_NAME_LENGTH - suffix.length();
            name = name.substring(0, Math.max(keep, 1)) + suffix;
        }
        return name;
    }

    /**
     * <b>存储名</b>（随机、服务端生成、与展示名完全分离）。
     *
     * <p>口径来源：{@code doc/data-model.md} §6.2 {@code flow_attachment.storage_path}
     * 「私有化本地存储**相对路径**，禁止公网直链」+ 本工作包「存储名与展示名分离」。
     * 用随机 UUID 而不是原名，使「客户端可控字符串」在服务器路径里**不存在**
     * —— 目录穿越在类型层面就不可能（另见 {@code LocalAttachmentStorage#resolve} 的第二道断言）。
     *
     * @param extension 已通过白名单的扩展名（小写、无点）
     */
    public static String storageName(String extension) {
        String ext = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        return UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
    }

    /** 取小写扩展名（无扩展名返回空串；{@code "a.tar.gz"} → {@code "gz"}）。 */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    // ================================================================ 工具

    private static Set<String> lower(JsonNode array) {
        Set<String> result = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    result.add(item.asText().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        java.util.Collections.sort(list);
        return list;
    }
}
