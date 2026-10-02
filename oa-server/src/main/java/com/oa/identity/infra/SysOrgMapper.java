package com.oa.identity.infra;

import com.oa.identity.domain.SysOrg;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 组织架构 Mapper（{@code sys_org}）。
 *
 * <p><b>不使用 MyBatis-Plus 的 {@code BaseMapper}</b>：{@code sys_org} 已登记为受控表
 * （{@code @DataScopeTable(table="sys_org", kind=NONE)}），BaseMapper 注入的
 * {@code selectById/selectList/selectPage} 等语句**不带 {@code @dataScope} 标记**，
 * 在已认证上下文会被 {@code DataScopeInterceptor} fail-closed 拒绝
 * （错误码 40303）。因此本 Mapper 的**读接口全部在 XML 中显式写标记**。
 *
 * <p>约定（doc/tech-design.md §5.3、{@code resources/mapper/identity/SysOrgMapper.xml} 头部注释）：
 * <ul>
 *   <li>任一 SELECT 的 WHERE 处必须写 {@code /* @dataScope(table=sys_org, alias=o) *}{@code /}；</li>
 *   <li>每张表/每条语句至多 **1 个**标记（拦截器只替换第一个标记）；</li>
 *   <li>写操作（insert/update）不受拦截器约束。</li>
 * </ul>
 */
@Mapper
public interface SysOrgMapper {

    /** 全量节点（导出与可见性推导用）。 */
    List<SysOrg> selectAll(@Param("includeDisabled") boolean includeDisabled);

    /** 按 id 取节点（逻辑删除过滤）。 */
    SysOrg selectById(@Param("id") Long id);

    /** 按 id 批量取（用于把 id 列表还原为节点，避免 N+1）。 */
    List<SysOrg> selectByIds(@Param("ids") Collection<Long> ids);

    /** 根节点（{@code parent_id IS NULL}）。 */
    List<SysOrg> selectRoots(@Param("includeDisabled") boolean includeDisabled);

    /**
     * 整棵子树（含自身），按 {@code path} 前缀匹配。
     *
     * @param pathPrefix 形如 {@code /1/12/}（后缀 {@code %} 由 SQL 拼）
     */
    List<SysOrg> selectSubtree(@Param("pathPrefix") String pathPrefix,
                               @Param("includeDisabled") boolean includeDisabled);

    /** 名称/路径关键字搜索。 */
    List<SysOrg> search(@Param("keyword") String keyword,
                        @Param("includeDisabled") boolean includeDisabled);

    /** 同类型节点数（校验「有且仅有 1 个集团根节点」，import-spec E-ORG-015）。 */
    int countByType(@Param("orgType") String orgType, @Param("excludeId") Long excludeId);

    /** 直接下级数量（删/停用前提示用）。 */
    int countChildren(@Param("parentId") Long parentId);

    /** 同父同名的数量（重名仅提示，不阻断：import-spec §3.2 org_name 允许同父重名）。 */
    int countSiblingName(@Param("parentId") Long parentId, @Param("name") String name,
                         @Param("excludeId") Long excludeId);

    // ------------------------------------------------------------------ 写

    /** 新增节点（自增主键回填到 {@code org.id}）。 */
    int insertOrg(SysOrg org);

    /** 改名 / 排序 / 备注（只更新非空字段由 SQL 显式列出，避免误清列）。 */
    int updateOrg(SysOrg org);

    /** 启用/停用。 */
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedBy") Long updatedBy);

    /** move 级联重算：单节点 path + depth。 */
    int updatePathAndDepth(@Param("id") Long id, @Param("path") String path, @Param("depth") Integer depth,
                           @Param("updatedBy") Long updatedBy);

    /** move 改父。 */
    int updateParent(@Param("id") Long id, @Param("parentId") Long parentId, @Param("updatedBy") Long updatedBy);

    /** 主负责人冗余回填（权威数据仍在 {@code sys_org_leader}）。 */
    int updateLeaderId(@Param("id") Long id, @Param("leaderId") Long leaderId, @Param("updatedBy") Long updatedBy);
}
