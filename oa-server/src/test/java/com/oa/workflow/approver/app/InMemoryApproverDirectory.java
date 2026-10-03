package com.oa.workflow.approver.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ApproverDirectory} 的**内存实现**（测试替身）。
 *
 * <p>9 条解析规则与预检、快照都必须能脱离数据库穷举验证（本工作包的验收要求），
 * 因此这里用最朴素的映射表提供组织 / 负责人 / 用户 / 角色成员：
 * <ul>
 *   <li>{@code orgs}：组织节点（含 parentId 与 path，供上溯与层级判断）；</li>
 *   <li>{@code primaryLeaders} / {@code deputyLeaders} / {@code categoryLeaders}：
 *       三类负责人绑定，键分别为「组织 id」「组织 id」「组织 id|业务线」；</li>
 *   <li>{@code users}：用户；{@code roleMembers}：角色 → 用户 id 列表。</li>
 * </ul>
 *
 * <p>本类**不**做数据域过滤（真实的 {@code JdbcApproverDirectory} 才需要，
 * 见 {@code JdbcApproverDirectorySystemScopeTest}）。
 */
public class InMemoryApproverDirectory implements ApproverDirectory {

    private final Map<Long, OrgNodeView> orgs = new LinkedHashMap<>();
    /** 负责人绑定存 **user_id**（而不是 Candidate 快照）：这样 {@link #resign(Long)} 之后
     *  负责人查询会像真实 SQL 一样（{@code u.status <> 'resigned'}）不再返回该人。 */
    private final Map<Long, List<Long>> primaryLeaderIds = new LinkedHashMap<>();
    private final Map<Long, List<Long>> deputyLeaderIds = new LinkedHashMap<>();
    private final Map<String, List<Long>> categoryLeaderIds = new LinkedHashMap<>();
    private final Map<Long, Candidate> users = new LinkedHashMap<>();
    private final Map<String, List<Long>> roleMembers = new LinkedHashMap<>();

    // ------------------------------------------------------------------ 装配

    public InMemoryApproverDirectory org(Long id, Long parentId, String orgType, String name, String path) {
        orgs.put(id, new OrgNodeView(id, parentId, orgType, name, path, path.split("/").length - 1, "active"));
        return this;
    }

    public InMemoryApproverDirectory user(Candidate candidate) {
        users.put(candidate.userId(), candidate);
        return this;
    }

    /** 便捷：造一个用户（组织信息取自 {@code orgId} 对应的节点）。 */
    public InMemoryApproverDirectory user(Long userId, String name, String employeeNo, Long orgId, Long companyId) {
        OrgNodeView org = orgs.get(orgId);
        return user(new Candidate(userId, name, "u" + userId, employeeNo, orgId,
                org == null ? null : org.name(), org == null ? null : org.path(), companyId, null, "active"));
    }

    public InMemoryApproverDirectory primary(Long orgId, Long userId) {
        requireUser(userId);
        primaryLeaderIds.computeIfAbsent(orgId, key -> new ArrayList<>()).add(userId);
        return this;
    }

    public InMemoryApproverDirectory deputy(Long orgId, Long userId) {
        requireUser(userId);
        deputyLeaderIds.computeIfAbsent(orgId, key -> new ArrayList<>()).add(userId);
        return this;
    }

    public InMemoryApproverDirectory category(Long orgId, String category, Long userId) {
        requireUser(userId);
        categoryLeaderIds.computeIfAbsent(orgId + "|" + category, key -> new ArrayList<>()).add(userId);
        return this;
    }

    public InMemoryApproverDirectory role(String roleCode, Long... userIds) {
        List<Long> members = new ArrayList<>(List.of(userIds));
        roleMembers.put(roleCode, members);
        return this;
    }

    /** 把某用户标记为离职（用于验证「离职人员不作为候选人」）。 */
    public InMemoryApproverDirectory resign(Long userId) {
        Candidate current = requireUser(userId);
        users.put(userId, new Candidate(current.userId(), current.name(), current.account(),
                current.employeeNo(), current.orgId(), current.orgName(), current.orgPath(),
                current.companyId(), current.position(), "resigned"));
        return this;
    }

    private Candidate requireUser(Long userId) {
        Candidate candidate = users.get(userId);
        if (candidate == null) {
            throw new IllegalStateException("测试夹具缺少用户 " + userId);
        }
        return candidate;
    }

    // ------------------------------------------------------------------ 端口实现

    @Override
    public Optional<OrgNodeView> org(Long orgId) {
        return Optional.ofNullable(orgs.get(orgId));
    }

    @Override
    public Optional<OrgNodeView> orgByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return orgs.values().stream().filter(org -> name.equals(org.name())).findFirst();
    }

    @Override
    public List<OrgNodeView> orgsByType(String orgType) {
        List<OrgNodeView> result = new ArrayList<>();
        for (OrgNodeView org : orgs.values()) {
            if (orgType != null && orgType.equalsIgnoreCase(org.orgType())) {
                result.add(org);
            }
        }
        return result;
    }

    @Override
    public List<Candidate> primaryLeaders(Long orgId) {
        return resolve(primaryLeaderIds.getOrDefault(orgId, List.of()));
    }

    @Override
    public List<Candidate> deputyLeaders(Long orgId) {
        return resolve(deputyLeaderIds.getOrDefault(orgId, List.of()));
    }

    @Override
    public List<Candidate> categoryLeaders(Long orgId, String category) {
        return resolve(categoryLeaderIds.getOrDefault(orgId + "|" + category, List.of()));
    }

    /** 绑定 id → 当前用户（离职者按真实 SQL 的口径过滤掉，因为真实查询带 {@code status <> 'resigned'}）。 */
    private List<Candidate> resolve(List<Long> userIds) {
        List<Candidate> result = new ArrayList<>();
        for (Long userId : userIds) {
            Candidate candidate = users.get(userId);
            if (candidate != null && !"resigned".equalsIgnoreCase(candidate.userStatus())) {
                result.add(candidate);
            }
        }
        return result;
    }

    @Override
    public Optional<Candidate> user(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public List<Candidate> users(List<Long> userIds) {
        List<Candidate> result = new ArrayList<>();
        if (userIds != null) {
            for (Long id : userIds) {
                Candidate candidate = users.get(id);
                // 与真实实现一致：离职/停用人员不返回（由规则层再做一次 assignable 过滤）
                if (candidate != null && candidate.assignable()) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    @Override
    public List<Candidate> usersByRoleCode(String roleCode) {
        List<Candidate> result = new ArrayList<>();
        for (Long id : roleMembers.getOrDefault(roleCode, List.of())) {
            Candidate candidate = users.get(id);
            if (candidate != null && candidate.assignable()) {
                result.add(candidate);
            }
        }
        return result;
    }
}
