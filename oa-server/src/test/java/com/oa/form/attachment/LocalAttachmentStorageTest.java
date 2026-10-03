package com.oa.form.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.form.attachment.app.LocalAttachmentStorage;
import com.oa.form.attachment.app.AttachmentStorage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>2b.7 私有存储</b>单测：落盘 / 读回 / 删除 / 目录穿越不可能 / 原子写入不留半个文件。
 *
 * <p>被测对象是 {@link LocalAttachmentStorage}（{@code AttachmentStorage} 的默认实现）。
 * 真源：{@code doc/forms.md} §1.4「存储 | 私有化本地存储，**不存公网**；下载必须经鉴权接口，
 * 禁止直链」；{@code doc/data-model.md} §6.2 {@code storage_path}「私有化本地存储**相对路径**」。
 */
class LocalAttachmentStorageTest {

    @TempDir
    Path root;

    private LocalAttachmentStorage storage() {
        OaProperties properties = new OaProperties();
        properties.getAttachment().setRoot(root.toString());
        return new LocalAttachmentStorage(properties);
    }

    private static byte[] content(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("落盘：返回相对路径（不含根）、大小与 sha256；物理文件确实在根之下")
    void storeWritesUnderRootAndReturnsRelativePath() throws Exception {
        LocalAttachmentStorage storage = storage();
        byte[] data = content("hello attachment");
        StoredFile stored;
        try (InputStream in = new ByteArrayInputStream(data)) {
            stored = storage.store("pdf", in, data.length);
        }

        assertThat(stored.relativePath()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.pdf");
        assertThat(stored.relativePath()).doesNotContain("..");
        assertThat(Path.of(stored.relativePath()).isAbsolute()).as("落库的是相对路径").isFalse();
        assertThat(stored.sizeBytes()).isEqualTo(data.length);
        assertThat(stored.sha256()).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));

        Path physical = root.resolve(stored.relativePath());
        assertThat(Files.isRegularFile(physical)).isTrue();
        assertThat(physical.normalize().startsWith(root)).isTrue();
        assertThat(Files.readAllBytes(physical)).isEqualTo(data);
        // 原子写入：不得留下 .part 临时文件
        try (Stream<Path> walk = Files.walk(root)) {
            assertThat(walk.filter(path -> path.getFileName().toString().endsWith(".part")).toList()).isEmpty();
        }
    }

    @Test
    @DisplayName("读回与删除：删除后物理文件消失，重复删除幂等（返回 false 而不是抛错）")
    void openAndDelete() throws Exception {
        LocalAttachmentStorage storage = storage();
        byte[] data = content("payload");
        StoredFile stored;
        try (InputStream in = new ByteArrayInputStream(data)) {
            stored = storage.store("png", in, data.length);
        }

        try (InputStream in = storage.open(stored.relativePath())) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
        assertThat(storage.exists(stored.relativePath())).isTrue();

        assertThat(storage.delete(stored.relativePath())).isTrue();
        assertThat(storage.exists(stored.relativePath())).isFalse();
        assertThat(storage.delete(stored.relativePath())).as("重复删除幂等").isFalse();
    }

    @Test
    @DisplayName("目录穿越不可能：../../ 与绝对路径都被 fail-closed 拒绝（40402，不泄露细节）")
    void pathTraversalIsImpossible() throws Exception {
        LocalAttachmentStorage storage = storage();
        byte[] data = content("x");
        StoredFile stored;
        try (InputStream in = new ByteArrayInputStream(data)) {
            stored = storage.store("pdf", in, data.length);
        }

        List<String> attacks = List.of(
                "../../../../etc/passwd",
                "..\\..\\windows\\win.ini",
                stored.relativePath().replace("/", "/../") + "/../../../escape.pdf",
                // **根目录之外**的绝对路径（不能用 root.resolve("x") —— 那仍在根之内，只是文件不存在）
                root.getParent().resolve("outside.pdf").toString(),
                "C:/Windows/System32/drivers/etc/hosts",
                "",
                "   ",
                "../" + stored.relativePath());

        for (String attack : attacks) {
            assertThatThrownBy(() -> storage.open(attack))
                    .as("open 必须拒绝 %s", attack)
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND));
            assertThatThrownBy(() -> storage.delete(attack))
                    .as("delete 必须拒绝 %s", attack)
                    .isInstanceOf(BizException.class);
            assertThat(storage.exists(attack)).as("exists 对非法路径返回 false").isFalse();
        }
        // 原件未被误删
        assertThat(storage.exists(stored.relativePath())).isTrue();
    }

    @Test
    @DisplayName("自己拼绝对路径越过根目录 → 拒绝；根目录本身 → 拒绝")
    void absolutePathOutsideRootIsRejected() {
        LocalAttachmentStorage storage = storage();
        assertThatThrownBy(() -> storage.open(root.getParent().resolve("x.pdf").toString()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> storage.open(root.toString()))
                .as("根目录本身不是附件").isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("写入字节数与声明不一致 → 拒并丢弃临时文件（不留半个附件）")
    void sizeMismatchDiscardsTempFile() throws Exception {
        LocalAttachmentStorage storage = storage();
        byte[] data = content("short");
        assertThatThrownBy(() -> {
            try (InputStream in = new ByteArrayInputStream(data)) {
                storage.store("pdf", in, data.length + 100);
            }
        }).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_POLICY_DENIED));

        assertThat(Files.exists(root)).isTrue();
        try (Stream<Path> walk = Files.walk(root)) {
            assertThat(walk.filter(Files::isRegularFile).toList()).as("失败不得留下任何文件").isEmpty();
        }
    }

    @Test
    @DisplayName("元数据在、物理文件缺失 → 404（不是 500，不泄露内部路径）")
    void missingPhysicalFileYieldsNotFound() {
        LocalAttachmentStorage storage = storage();
        assertThatThrownBy(() -> storage.open("2026/10/03/0123456789abcdef0123456789abcdef.pdf"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("根目录默认在仓库之外（$user.home/.oa/attachments），dev 也不指向静态资源目录")
    void defaultRootIsOutsideRepository() {
        OaProperties properties = new OaProperties();
        Path resolved = properties.getAttachment().resolvedRoot();
        assertThat(resolved.isAbsolute()).isTrue();
        assertThat(resolved.toString()).contains(".oa");
        assertThat(resolved.toString().toLowerCase()).doesNotContain("target")
                .doesNotContain("static")
                .doesNotContain("webapp")
                .doesNotContain("resources");
        assertThat(resolved.startsWith(Path.of(System.getProperty("user.home")))).isTrue();
    }

    @Test
    @DisplayName("落盘内容哈希可用于防替换校验（sha256 与内容一一对应）")
    void sha256DetectsReplacement() throws Exception {
        LocalAttachmentStorage storage = storage();
        StoredFile first;
        try (InputStream in = new ByteArrayInputStream(content("original"))) {
            first = storage.store("pdf", in, content("original").length);
        }
        StoredFile second;
        try (InputStream in = new ByteArrayInputStream(content("tampered"))) {
            second = storage.store("pdf", in, content("tampered").length);
        }
        assertThat(first.sha256()).isNotEqualTo(second.sha256());
        try (InputStream in = storage.open(first.relativePath())) {
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            assertThat(actual).isEqualTo(first.sha256());
        }
    }

    @Test
    @DisplayName("IOException 分支：根目录被文件占位 → 50004（不是 50000 泛化）")
    void unwritableRootYieldsStorageFailed() throws IOException {
        Path blocked = root.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        OaProperties properties = new OaProperties();
        properties.getAttachment().setRoot(blocked.toString());
        LocalAttachmentStorage storage = new LocalAttachmentStorage(properties);
        byte[] data = content("x");
        assertThatThrownBy(() -> {
            try (InputStream in = new ByteArrayInputStream(data)) {
                storage.store("pdf", in, data.length);
            }
        }).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_STORAGE_FAILED));
    }
}
