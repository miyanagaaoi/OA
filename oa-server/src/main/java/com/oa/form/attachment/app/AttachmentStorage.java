package com.oa.form.attachment.app;

import java.io.InputStream;

/**
 * <b>附件私有存储抽象</b>（阶段 2b.7）—— 应用层只依赖本接口，便于将来换对象存储。
 *
 * <h2>为什么要有这层抽象</h2>
 * <p>{@code doc/tech-design.md} §12 D6 定稿「文件存储 = **本地私有目录 + 鉴权下载**（备选 MinIO）」，
 * 风险项「文件存储单点 → 本地卷 + 每日备份覆盖文件目录；{@code sha256} 校验；下载鉴权」。
 * 把「本地目录」写死在服务层会让换存储变成改业务代码；本接口把可替换点收敛到**一个实现类**。
 *
 * <h2>三条不可回退的契约（实现方必须守住）</h2>
 * <ol>
 *   <li><b>路径由服务端生成</b>：{@link #store} 只接受「扩展名 + 内容」，**不接受客户端路径或文件名**。
 *       返回的相对路径是服务器自己的产物；调用方不得把用户输入拼进来；</li>
 *   <li><b>目录穿越不可能</b>：{@link #open} / {@link #delete} 只接受 {@link #store} 返回过的相对路径，
 *       实现方必须做「解析后仍在根目录之下」的断言（fail-closed），而不是靠字符串匹配 {@code ".."}；</li>
 *   <li><b>不落静态资源目录</b>：根目录在仓库与 Web 根之外，本工程不注册任何静态资源映射，
 *       因此 {@code storage_path} 没有可直链的 URL（AC-45「直接访问存储路径返回 403」的拓扑保证）。</li>
 * </ol>
 */
public interface AttachmentStorage {

    /**
     * 落盘一个附件（服务端生成存储名）。
     *
     * @param extension 已通过白名单的扩展名（小写、无点；可为空）
     * @param content   内容流（由调用方负责关闭；实现方**不得**关闭入参流）
     * @param sizeBytes 期望字节数（实现方校验实际写入量，不一致即失败）
     * @return 落盘结果（相对路径 + 实际大小 + SHA-256）
     */
    StoredFile store(String extension, InputStream content, long sizeBytes);

    /** 打开一个已落盘的文件（不存在由实现方抛 {@code BizException} 的 404 族）。 */
    InputStream open(String relativePath);

    /**
     * 删除物理文件。
     *
     * @return {@code true} = 确实删掉了；{@code false} = 文件本来就不在（幂等，视为成功）
     */
    boolean delete(String relativePath);

    /** 是否存在（用于删除前自检与诊断；不抛异常）。 */
    boolean exists(String relativePath);

    /** 存储根目录的**可诊断**标识（日志/出参用；绝不回给客户端的文件系统全路径）。 */
    String describeRoot();

    /**
     * 落盘结果。
     *
     * @param relativePath 相对存储根的路径（写入 {@code flow_attachment.storage_path}）
     * @param sizeBytes    实际写入字节数
     * @param sha256       内容哈希（{@code flow_attachment.sha256}，防替换）
     */
    record StoredFile(String relativePath, long sizeBytes, String sha256) {
    }
}
