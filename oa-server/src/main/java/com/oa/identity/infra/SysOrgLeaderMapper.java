package com.oa.identity.infra;

import com.oa.identity.domain.SysOrgLeader;
import com.oa.identity.infra.row.LeaderCandidateRow;
import com.oa.identity.infra.row.LeaderRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 组织负责人 Mapper（{@code sys_org_leader}）。
 *
 * <p>{@code sys_org_leader} 已登记为受控表（{@code kind=NONE}）：SELECT 必须带
 * {@code /* @dataScope(table=sys_org_leader, alias=ol) *}{@code /} 标记，否则 fail-closed。
 *
 * <p>{@link #selectCandidates} 的主过滤表是 {@code sys_user}（成员范围应按**用户类口径**
 * 收敛），因此该语句的标记指向 {@code sys_user}；拦截器只替换**第一个**标记，
 * 故 join 进来的 {@code sys_org_leader} / {@code sys_user_position} 仅作展示与标注之用。
 */
@Mapper
public interface SysOrgLeaderMapper {

    /** 该组织的全部负责人（含姓名/工号/业务线），展示用。 */
    List<LeaderRow> selectByOrgId(@Param("orgId") Long orgId);

    /** 单条（含姓名等展示字段）。 */
    LeaderRow selectRowById(@Param("id") Long id);

    /** 单条实体（供服务层校验归属关系：必须确认该绑定属于路径上的那个组织）。 */
    SysOrgLeader selectEntityById(@Param("id") Long id);

    /** 该用户担任负责人的组织清单（{@code /users/{id}/leader-of}）。 */
    List<LeaderRow> selectByUserId(@Param("userId") Long userId);

    /** 集团层已按业务线绑定的分管领导（{@code category IS NOT NULL}）。 */
    List<LeaderRow> selectGroupLines();

    /** 该组织 + 该业务线的绑定（{@code category} 为 {@code null} 时即普通负责人组）。 */
    List<SysOrgLeader> selectBindings(@Param("orgId") Long orgId, @Param("category") String category);

    /** 唯一键口径的单条绑定：{@code (org_id, user_id, leader_type, category)}。 */
    SysOrgLeader selectBinding(@Param("orgId") Long orgId, @Param("userId") Long userId,
                               @Param("leaderType") String leaderType, @Param("category") String category);

    /** 负责人候选人（该组织成员 + 是否已任职标注）；{@code keyword} 为空即不过滤。 */
    List<LeaderCandidateRow> selectCandidates(@Param("orgId") Long orgId, @Param("keyword") String keyword);

    /**
     * 已设**正职**的组织 id 集合（{@code leader_type='primary' AND category IS NULL}）。
     *
     * <p>用途：组织树节点的 {@code hasPrimaryLeader}（AC-11「未设正职」标示）。
     * 刻意做成**一次批量查询**（{@code DISTINCT org_id}），避免逐节点查询造成 N+1。
     * 口径说明：业务线分管领导（{@code category} 非空）不算「正职」，故必须带
     * {@code category IS NULL} 条件。
     */
    List<Long> selectPrimaryOrgIds();

    /** 该用户作为负责人的绑定条数（离职前提示：仍担任负责人）。 */
    int countByUserId(@Param("userId") Long userId);

    int insertLeader(SysOrgLeader leader);

    /** 只更新可改字段（正/副职、岗位名、业务线、排序、备注、生效区间）。 */
    int updateLeader(SysOrgLeader leader);

    /**
     * 批量导入 upsert（import-spec §6.1）：四元组命中后**只更新** {@code sort_no} 与 {@code remark}。
     *
     * <p>刻意不复用 {@link #updateLeader}（全字段覆盖式 PUT）：导入模板不含
     * {@code duty_title} 与生效区间，若用全字段更新会把后台维护的岗位名与生效期清空。
     */
    int updateLeaderFromImport(@Param("id") Long id, @Param("sortNo") Integer sortNo,
                               @Param("remark") String remark, @Param("updatedBy") Long updatedBy);

    /** 负责人主数据导出（{@code org_leader.csv}，import-spec §9.1）：与列表同口径，不分页。 */
    List<LeaderRow> selectAllForExport();

    int deleteById(@Param("id") Long id);
}
