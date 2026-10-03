package com.oa.workflow.definition.infra;

import com.oa.workflow.definition.domain.FlowTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程模板 Mapper（{@code flow_template}，doc/data-model.md §4.1）。
 *
 * <h2>为什么**不需要** {@code @dataScope} 标记</h2>
 * <p>{@code flow_template} 是**配置数据**，未登记为数据域受控表
 * （{@code application.yml} 的 {@code oa.scope.tables} 只登记
 * {@code flow_instance} / {@code form_data} / {@code flow_task} / {@code flow_routing} / {@code sys_user}，
 * 实体注解再加 {@code sys_org} / {@code sys_org_leader} / {@code sys_user_position}）。
 * {@code DataScopeInterceptor} 只在 SQL 命中受控表正则时要求标记，
 * 因此本 Mapper 的 SELECT 既**不会**被 fail-closed 拒绝，也**不应该**加标记
 * （加了会被替换成实例类数据域片段，按调用人的单据可见范围去裁剪**模板**，属错误织入）。
 * 这一「不加标记」的口径由 {@code WorkflowMapperXmlTest} 正向断言：
 * 该文件里不得出现 {@code @dataScope(}；同时 {@code flow_template} / {@code flow_node}
 * **不得**出现在受控表清单里（否则既有配置会被静默裁剪）。
 *
 * <p>访问控制由 {@code WorkflowPermissionService}（{@code admin:flow:template} /
 * {@code admin:flow:node} / {@code admin:flow:publish}）承担 —— 见 {@code FlowConfigPermission}。
 *
 * <p><b>不继承 {@code BaseMapper}</b>：受控表 Mapper 的铁律（{@code DataScopeMapperGuardTest}）；
 * 这里虽非受控表，但保持同一条纪律，避免后人误用 MP 注入的无标记语句读到业务表。
 */
@Mapper
public interface FlowTemplateMapper {

    /** 按单据类型列出全部版本（版本历史）。 */
    List<FlowTemplate> selectByCode(@Param("code") String code);

    /** 按单据类型 + 状态列出（草稿 / 已发布 / 已归档）。 */
    List<FlowTemplate> selectByCodeAndStatus(@Param("code") String code, @Param("status") String status);

    /** 全量列表（{@code GET /flow-templates}：可按 code / status / formType 过滤，全部为空即全量）。 */
    List<FlowTemplate> selectTemplates(@Param("code") String code,
                                  @Param("formType") String formType,
                                  @Param("status") String status);

    /** 按 id 取模板。 */
    FlowTemplate selectTemplateById(@Param("id") Long id);

    /** 按 {@code (code, version)} 取（**在途锁版本**的唯一读取口径，templates.md V-02）。 */
    FlowTemplate selectByCodeAndVersion(@Param("code") String code, @Param("version") Integer version);

    /** 该单据类型的最大版本号（{@code null} 表示尚无版本）。 */
    Integer selectMaxVersion(@Param("code") String code);

    // ------------------------------------------------------------------ 写

    int insertTemplate(FlowTemplate template);

    /** 草稿可写字段（元数据 + 闸门配置 + 节点数）。 */
    int updateDraft(FlowTemplate template);

    /** 状态流转（发布 / 归档），发布时写 {@code published_at}。 */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("updatedBy") Long updatedBy);

    /** 发布新版本时把同单据类型的**其它**已发布版本转为 {@code archived}（templates.md §4.1 第 5 步）。 */
    int archiveOtherPublished(@Param("code") String code, @Param("excludeId") Long excludeId,
                              @Param("updatedBy") Long updatedBy);

    /** 发布时回写节点数（与 {@code flow_node} 实际行数一致）。 */
    int updateNodeCount(@Param("id") Long id, @Param("nodeCount") Integer nodeCount,
                        @Param("updatedBy") Long updatedBy);
}
