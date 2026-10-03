package com.oa.workflow.approver.infra;

import com.oa.workflow.approver.infra.row.CandidateRow;
import com.oa.workflow.approver.infra.row.OrgRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审批人解析的目录查询（{@code oa.workflow.approver.rule-table} 的数据访问）。
 *
 * <h2>数据域纪律（与本工程全部受控表 Mapper 同一套）</h2>
 * <ul>
 *   <li>本文件查询的 {@code sys_org} / {@code sys_org_leader} / {@code sys_user} **全部**是受控表
 *       （{@code application.yml} 的 {@code oa.scope.tables} + 实体 {@code @DataScopeTable}），
 *       因此**每条 SELECT 都带且只带 1 个** {@code /* @dataScope(table=..., alias=...) *}{@code /} 标记；</li>
 *   <li>标记指向**承担过滤语义**的那张表：按组织过滤 → {@code sys_org}；
 *       按负责人绑定过滤 → {@code sys_org_leader}；按人/角色过滤 → {@code sys_user}；</li>
 *   <li>标记位置在 {@code WHERE 1 = 1} 之后，可容纳一个完整布尔表达式（片段缺失即悬空 AND → fail-closed）；</li>
 *   <li><b>不继承 {@code BaseMapper}</b>：MP 注入语句不带标记，在已认证上下文会被 40303 拒绝
 *       （守卫测试 {@code DataScopeMapperGuardTest} 已把本接口列入受控 Mapper 清单）。</li>
 * </ul>
 *
 * <p><b>调用方必须使用系统口径</b>：审批人解析服务于「发起时固化快照」，
 * 语义上是系统口径取人（发起人的数据域不能决定财务部负责人是否存在），
 * 因此 {@code JdbcApproverDirectory} 的所有调用都包裹在
 * {@code DataScopeContext.system()} 中（片段退化为 {@code 1=1}）。
 * 这一点在类注释里写明，避免被误判为「数据域旁路」。
 */
@Mapper
public interface ApproverDirectoryMapper {

    /** 按 id 取组织节点（逻辑删除过滤）。 */
    OrgRow selectOrg(@Param("orgId") Long orgId);

    /** 按名称取组织节点（同名多节点时取 id 最小者；集团财务部按名称兜底定位）。 */
    OrgRow selectOrgByName(@Param("name") String name);

    /** 按类型取组织节点（{@code group} / {@code company}）。 */
    List<OrgRow> selectOrgsByType(@Param("orgType") String orgType);

    /** 组织正职负责人（{@code leader_type='primary' AND category IS NULL}）。 */
    List<CandidateRow> selectPrimaryLeaders(@Param("orgId") Long orgId);

    /** 组织副职负责人（{@code leader_type='deputy'}）—— ③分公司分管领导的解析源。 */
    List<CandidateRow> selectDeputyLeaders(@Param("orgId") Long orgId);

    /** 组织按业务线（事项类别）绑定的正职负责人。 */
    List<CandidateRow> selectCategoryLeaders(@Param("orgId") Long orgId, @Param("category") String category);

    /** 按用户 id 取候选人。 */
    CandidateRow selectUser(@Param("userId") Long userId);

    /** 批量取候选人（保序由调用方负责）。 */
    List<CandidateRow> selectUsers(@Param("ids") Collection<Long> ids);

    /** 角色下的在职用户（{@code designated} 的 {@code role_code} 参数）。 */
    List<CandidateRow> selectUsersByRoleCode(@Param("roleCode") String roleCode);
}
