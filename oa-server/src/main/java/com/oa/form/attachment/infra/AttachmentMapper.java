package com.oa.form.attachment.infra;

import com.oa.form.attachment.domain.Attachment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 附件元数据 Mapper（{@code flow_attachment}，doc/data-model.md §6.2）—— 阶段 2b.7。
 *
 * <h2>受控表纪律（fail-closed）</h2>
 * <ul>
 *   <li>{@code flow_attachment} 由 {@link Attachment} 上的 {@code @DataScopeTable(kind=NONE)}
 *       登记为**受控表**：每条 SELECT **恰好 1 个** {@code @dataScope} 标记，否则 40303；</li>
 *   <li>本表没有 {@code initiator_id} 一族列（{@code DataScopeSqlBuilder} 生成的条件全部依赖它们），
 *       因此**过滤主体恒为 {@code flow_instance}**：每条 SELECT 都
 *       {@code JOIN flow_instance i ON i.id = a.instance_id} 并写
 *       {@code /* @dataScope(table=flow_instance, alias=i) *}{@code /}
 *       —— 口径与 {@code FlowRoutingMapper} / {@code FormDataMapper} 一致；</li>
 *   <li><b>不继承 {@code BaseMapper}</b>：MP 注入的语句没有标记，在已认证上下文会被 40303 拒绝
 *       （{@code DataScopeMapperGuardTest} 把这条变成机器可验证的硬约束）。</li>
 * </ul>
 *
 * <p>写语句（insert/delete）不需要标记 —— 拦截器只约束 SELECT。
 */
@Mapper
public interface AttachmentMapper {

    /** 按 id 取附件（数据域过滤：域外返回 {@code null}，调用方按 404 处理）。 */
    Attachment selectById(@Param("id") Long id);

    /** 某单据的全部附件（数据域过滤；按轮次与上传时间排序）。 */
    List<Attachment> selectByInstance(@Param("instanceId") Long instanceId);

    /** 某单据某字段的附件数（数据域过滤）—— 单字段数量上限的取数口径。 */
    int countByInstanceAndField(@Param("instanceId") Long instanceId, @Param("fieldCode") String fieldCode);

    /** 某单据附件总数（含补件，数据域过滤）—— §1.4「≤50 个（含补件）」的取数口径。 */
    int countByInstance(@Param("instanceId") Long instanceId);

    // ------------------------------------------------------------------ 写

    /** 落一条附件元数据（返回自增主键）。 */
    int insert(Attachment attachment);

    /** 删除一条附件元数据（物理文件由存储层删除；顺序见 {@code AttachmentService#delete}）。 */
    int deleteById(@Param("id") Long id);
}
