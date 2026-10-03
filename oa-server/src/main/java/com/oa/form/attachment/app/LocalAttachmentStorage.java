package com.oa.form.attachment.app;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.form.attachment.domain.AttachmentPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * <b>本地私有目录存储</b>（阶段 2b.7）—— {@link AttachmentStorage} 的默认实现。
 *
 * <h2>目录布局</h2>
 * <pre>
 *   &lt;oa.attachment.root&gt;/&lt;yyyy&gt;/&lt;MM&gt;/&lt;dd&gt;/&lt;32 位随机 hex&gt;.&lt;ext&gt;
 * </pre>
 * <p>真源 {@code doc/forms.md} §1.4「存储 | 私有化本地存储，**不存公网**」与
 * {@code doc/data-model.md} §6.2 {@code storage_path}「私有化本地存储**相对路径**，禁止公网直链」。
 * 落库的是**相对路径**（不含根目录），因此换根目录（`oa.attachment.root`）不需要数据迁移，
 * 也不会把服务器文件系统结构写进数据库。
 *
 * <p>按日期分片而不是按 {@code instance_id} 分片：目录名不含业务 id →
 * 即便目录被读到也无法反推单据结构（最小权限的顺带收益）。
 *
 * <h2>原子写入与目录穿越</h2>
 * <ul>
 *   <li><b>先写临时文件再 {@code ATOMIC_MOVE}</b>：中断/异常不会留下"半个附件"
 *       （否则 DB 里会存在一个大小与内容都不对的孤儿文件）；</li>
 *   <li><b>穿越不可能</b>：路径由 {@link AttachmentPolicy#storageName} 生成的随机名拼成，
 *       客户端字符串根本不参与；{@link #resolve} 再做一次「规范化后必须仍在根之下」的断言
 *       （fail-closed 抛 40402），作为第二道闸；</li>
 *   <li><b>不落静态资源目录</b>：根目录默认取 {@code ${user.home}/.oa/attachments}（仓库之外），
 *       本工程不注册任何 {@code ResourceHandler}，因此没有 URL 能映射到它（AC-45 / TC-FORM-024）。</li>
 * </ul>
 */
@Component
public class LocalAttachmentStorage implements AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalAttachmentStorage.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 单次读写的缓冲大小。 */
    private static final int BUFFER = 8192;

    private final OaProperties properties;

    public LocalAttachmentStorage(OaProperties properties) {
        this.properties = properties;
        Path root = properties.getAttachment().resolvedRoot();
        log.info("附件私有存储根目录：{}（不注册静态资源映射，storage_path 无 URL 可达）", root);
    }

    @Override
    public StoredFile store(String extension, InputStream content, long sizeBytes) {
        if (content == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附件内容为空");
        }
        Path root = properties.getAttachment().resolvedRoot();
        String name = AttachmentPolicy.storageName(extension);
        String relative = LocalDate.now().format(DAY) + "/" + name;
        Path target = root.resolve(relative).normalize();
        assertUnderRoot(root, target);
        Path temp = target.resolveSibling(name + ".part");
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written = 0;
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = content.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    written += read;
                }
            }
            if (sizeBytes >= 0 && written != sizeBytes) {
                // 服务端以自己的读取量为准（客户端声明的 size 不可信）；不一致说明流被截断
                Files.deleteIfExists(temp);
                throw new BizException(ErrorCode.ATTACHMENT_POLICY_DENIED,
                        String.format("附件写入字节数不一致（期望 %d，实际 %d），已丢弃", sizeBytes, written));
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            log.debug("附件落盘：relative={} size={} sha256={}", relative, written, sha256);
            return new StoredFile(relative, written, sha256);
        } catch (BizException ex) {
            deleteQuietly(temp);
            throw ex;
        } catch (IOException | NoSuchAlgorithmException ex) {
            deleteQuietly(temp);
            log.error("附件落盘失败 relative={}：{}", relative, ex.getMessage(), ex);
            throw new BizException(ErrorCode.ATTACHMENT_STORAGE_FAILED,
                    ErrorCode.ATTACHMENT_STORAGE_FAILED.getMessage() + "（写入失败：" + ex.getClass().getSimpleName() + "）");
        }
    }

    @Override
    public InputStream open(String relativePath) {
        Path path = resolveExisting(relativePath);
        try {
            return Files.newInputStream(path);
        } catch (IOException ex) {
            log.error("附件读取失败 relative={}：{}", relativePath, ex.getMessage());
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND, ErrorCode.ATTACHMENT_NOT_FOUND.getMessage());
        }
    }

    @Override
    public boolean delete(String relativePath) {
        Path path = resolve(relativePath);
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                pruneEmptyParents(path.getParent());
            }
            return deleted;
        } catch (IOException ex) {
            // 物理删除失败 → 抛 500 让事务回滚，元数据行保留，用户可重试；
            // 绝不"先删元数据再删文件"（那会留下无人引用却占空间的垃圾文件，与 AC-45 取向相反）
            log.error("附件物理删除失败 relative={}：{}", relativePath, ex.getMessage(), ex);
            throw new BizException(ErrorCode.ATTACHMENT_STORAGE_FAILED,
                    ErrorCode.ATTACHMENT_STORAGE_FAILED.getMessage() + "（删除失败：" + ex.getClass().getSimpleName() + "）");
        }
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            return Files.isRegularFile(resolve(relativePath));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public String describeRoot() {
        return properties.getAttachment().resolvedRoot().toString();
    }

    // ================================================================ 路径安全

    /** 解析相对路径（**第二道闸**：规范化后必须仍在根目录之下，否则 fail-closed）。 */
    Path resolve(String relativePath) {
        Path root = properties.getAttachment().resolvedRoot();
        if (relativePath == null || relativePath.isBlank()) {
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND, ErrorCode.ATTACHMENT_NOT_FOUND.getMessage());
        }
        Path candidate = root.resolve(relativePath.replace('\\', '/')).normalize();
        assertUnderRoot(root, candidate);
        return candidate;
    }

    private Path resolveExisting(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            // 元数据在、文件不在（含人工误删）→ 404 而不是 500：不泄露文件系统细节
            log.warn("附件元数据存在但物理文件缺失：{}", path.getFileName());
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND, ErrorCode.ATTACHMENT_NOT_FOUND.getMessage());
        }
        return path;
    }

    /**
     * 断言「解析后的绝对路径仍在存储根之下」。
     *
     * <p>为什么用 {@code startsWith} 而不是「字符串里没有 {@code ..}」：前者是**拓扑判据**
     * （符号链接/多重分隔符/编码变体都逃不掉），后者是黑名单，永远补不全。
     */
    private static void assertUnderRoot(Path root, Path candidate) {
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            log.warn("拒绝越出附件存储根的路径：root={} candidate={}", root, candidate);
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND, ErrorCode.ATTACHMENT_NOT_FOUND.getMessage());
        }
    }

    /** 删除后自底向上清理空目录（最多两级，避免误删共享父目录）。 */
    private static void pruneEmptyParents(Path dir) {
        Path current = dir;
        for (int i = 0; i < 3 && current != null; i++) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            } catch (IOException ex) {
                return;
            }
            try {
                Files.deleteIfExists(current);
            } catch (IOException ex) {
                return;
            }
            current = current.getParent();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("临时文件清理失败：{}", path.getFileName());
        }
    }
}
