package com.oa.workflow.approver.app;

import java.util.List;
import java.util.Optional;

/**
 * 审批人解析所需的**身份目录端口**（{@code oa.workflow.approver.rule-table} 的唯一外部依赖）。
 *
 * <h2>为什么做成端口而不是直接注入 Mapper</h2>
 * <p>9 条解析规则必须**每条都能被独立单测**（本工作包的验收要求），而规则本身就是
 * 「读组织链 / 读负责人 / 读角色成员」的纯组合。把数据访问收敛到本接口后，
 * 单测用内存实现即可穷举全部边界（科室无负责人、公司无总经理、角色为空、发起人自选为空……），
 * 生产实现见 {@code com.oa.workflow.approver.infra.JdbcApproverDirectory}（带 {@code @dataScope} 标记）。
 *
 * <h2>口径约定（对全部实现生效）</h2>
 * <ul>
 *   <li><b>正职 vs 副职</b>：{@link #primaryLeaders(Long)} 只返回
 *       {@code leader_type='primary' AND category IS NULL}（import-spec §4.4：部门/科室层审批人解析取正职；
 *       公司层则「总经理=正职、分公司分管领导=副职」）；
 *       {@link #deputyLeaders(Long)} 只返回 {@code leader_type='deputy'}（③分公司分管领导的解析源）；</li>
 *   <li><b>业务线分管领导</b>：{@link #categoryLeaders(Long, String)} 的 {@code category} 非空，
 *       且按 import-spec E-LEAD-008 只可能绑在集团层节点上；</li>
 *   <li><b>人员有效性</b>：实现方**必须过滤离职/停用**（{@code sys_user.status <> 'resigned'} 且未逻辑删除），
 *       否则空候选人拦截（AC-11）会失效；</li>
 *   <li><b>数据域</b>：解析发生在**发起时**、服务于该单据的审批人快照，属系统口径；
 *       生产实现由 {@code DataScopeContext.system()} 包裹并带 {@code @dataScope} 标记
 *       （与 {@code oa.identity.block-on-inflight} 同一纪律）。</li>
 * </ul>
 */
public interface ApproverDirectory {

    /** 按 id 取组织节点。 */
    Optional<OrgNodeView> org(Long orgId);

    /** 按名称取唯一组织节点（集团财务部按名称兜底定位；同名多节点时取 id 最小者）。 */
    Optional<OrgNodeView> orgByName(String name);

    /** 按类型取组织节点（{@code group} / {@code company}）。 */
    List<OrgNodeView> orgsByType(String orgType);

    /** 该组织的**正职**负责人（{@code leader_type='primary' AND category IS NULL}）。 */
    List<Candidate> primaryLeaders(Long orgId);

    /** 该组织的**副职**负责人（{@code leader_type='deputy'}）。 */
    List<Candidate> deputyLeaders(Long orgId);

    /** 该组织按**业务线（事项类别）**绑定的正职负责人。 */
    List<Candidate> categoryLeaders(Long orgId, String category);

    /** 按用户 id 取候选人（缺失返回 {@code Optional.empty()}）。 */
    Optional<Candidate> user(Long userId);

    /** 批量取候选人（保序；缺失的 id 直接跳过）。 */
    List<Candidate> users(List<Long> userIds);

    /** 持有该角色码的**在职**用户。 */
    List<Candidate> usersByRoleCode(String roleCode);
}
