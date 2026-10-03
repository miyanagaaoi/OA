package com.oa.workflow.definition.infra;

import com.oa.workflow.definition.domain.FlowNode;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程节点 Mapper（{@code flow_node}，doc/data-model.md §4.2）。
 *
 * <p>与 {@link FlowTemplateMapper} 同一纪律：{@code flow_node} 是**配置数据**，未登记为数据域受控表，
 * 因此 SELECT **刻意不带** {@code @dataScope} 标记（加了会按调用人的单据可见范围裁剪模板配置），
 * 访问控制由 {@code WorkflowPermissionService}（{@code admin:flow:node}）承担。
 * 该口径由 {@code WorkflowMapperXmlTest} 正向断言。
 *
 * <p>{@code seq} 的唯一键是 {@code uk_flow_node_seq (template_id, seq)}：因此「换序」必须
 * 先挪到临时序号再落位（见 {@code FlowDefinitionService#reorder}），不能直接两条 UPDATE 交叉。
 */
@Mapper
public interface FlowNodeMapper {

    /** 模板下的全部节点（按 seq）。 */
    List<FlowNode> selectByTemplateId(@Param("templateId") Long templateId);

    /** 批量取多个模板的节点（避免 N+1）。 */
    List<FlowNode> selectByTemplateIds(@Param("templateIds") Collection<Long> templateIds);

    /** 按 id 取节点。 */
    FlowNode selectNodeById(@Param("id") Long id);

    /** 模板下的节点数（发布前校验与回写 node_count）。 */
    int countByTemplateId(@Param("templateId") Long templateId);

    /** 同模板下该节点码是否已存在（主干节点不允许重复）。 */
    int countByTemplateAndCode(@Param("templateId") Long templateId,
                              @Param("nodeCode") String nodeCode,
                              @Param("excludeId") Long excludeId);

    // ------------------------------------------------------------------ 写

    int insertNode(FlowNode node);

    /** 全字段更新（名称/类型/解析规则/决议/阈值/签名/超时/开关/跳过条件）。 */
    int updateNode(FlowNode node);

    /** 单独改 seq（换序用，先置临时位再落位）。 */
    int updateSeq(@Param("id") Long id, @Param("seq") Integer seq);

    int deleteById(@Param("id") Long id);

    /** 删除模板下的全部节点（仅草稿版本复用；历史版本禁止删除节点）。 */
    int deleteByTemplateId(@Param("templateId") Long templateId);

    /** 克隆一个版本的节点到新版本（同单据类型开新草稿）。 */
    int cloneNodes(@Param("fromTemplateId") Long fromTemplateId, @Param("toTemplateId") Long toTemplateId);
}
