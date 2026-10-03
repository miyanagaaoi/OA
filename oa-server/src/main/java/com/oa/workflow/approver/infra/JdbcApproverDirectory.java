package com.oa.workflow.approver.infra;

import com.oa.common.scope.DataScopeContext;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.app.OrgNodeView;
import com.oa.workflow.approver.infra.row.CandidateRow;
import com.oa.workflow.approver.infra.row.OrgRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ApproverDirectory} 的 JDBC 实现（数据访问见 {@link ApproverDirectoryMapper}）。
 *
 * <h2>为什么所有查询都必须显式使用系统口径</h2>
 * <p>审批人解析的语义是「<b>发起时把这张单将经过的人固化下来</b>」：
 * <ul>
 *   <li>它读的是**别人**（财务部负责人、集团董事长、公司总经理……），
 *       若按发起人的数据域过滤，分公司员工发起时会「看不到」集团董事长 →
 *       候选人集合为空 → 被 AC-11 拦截，**全系统无人能发起单据**；</li>
 *   <li>它也不该按审批人的数据域反过来裁剪（那会让快照随调用人变化，破坏快照的确定性）。</li>
 * </ul>
 * 因此本实现用 {@link #systemScope(Supplier)} 把每次查询包在 {@link DataScopeContext#system()} 里，
 * 与 {@code DefaultInFlightChecker}（离职/停用前的影响面检查）是同一套已定稿的
 * 「系统口径统计查询」模式。**这不是数据域旁路**：这里的读取不产生任何授权后果，
 * 只决定「这张单的审批人是谁」；单据读取仍然走数据域织入
 * （{@code FlowInstanceMapper} 的实例查询带 {@code @dataScope} 标记且**不**用系统口径）。
 */
@Component
public class JdbcApproverDirectory implements ApproverDirectory {

    private static final Logger log = LoggerFactory.getLogger(JdbcApproverDirectory.class);

    private final ApproverDirectoryMapper mapper;

    public JdbcApproverDirectory(ApproverDirectoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OrgNodeView> org(Long orgId) {
        if (orgId == null) {
            return Optional.empty();
        }
        return systemScope(() -> Optional.ofNullable(mapper.selectOrg(orgId)).map(JdbcApproverDirectory::toView));
    }

    @Override
    public Optional<OrgNodeView> orgByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return systemScope(() -> Optional.ofNullable(mapper.selectOrgByName(name.trim()))
                .map(JdbcApproverDirectory::toView));
    }

    @Override
    public List<OrgNodeView> orgsByType(String orgType) {
        if (orgType == null || orgType.isBlank()) {
            return List.of();
        }
        return systemScope(() -> {
            List<OrgNodeView> views = new ArrayList<>();
            for (OrgRow row : mapper.selectOrgsByType(orgType.trim().toLowerCase(java.util.Locale.ROOT))) {
                views.add(toView(row));
            }
            return views;
        });
    }

    @Override
    public List<Candidate> primaryLeaders(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        return systemScope(() -> toCandidates(mapper.selectPrimaryLeaders(orgId)));
    }

    @Override
    public List<Candidate> deputyLeaders(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        return systemScope(() -> toCandidates(mapper.selectDeputyLeaders(orgId)));
    }

    @Override
    public List<Candidate> categoryLeaders(Long orgId, String category) {
        if (orgId == null || category == null || category.isBlank()) {
            return List.of();
        }
        return systemScope(() -> toCandidates(mapper.selectCategoryLeaders(orgId, category.trim())));
    }

    @Override
    public Optional<Candidate> user(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return systemScope(() -> Optional.ofNullable(mapper.selectUser(userId)).map(JdbcApproverDirectory::toView));
    }

    @Override
    public List<Candidate> users(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : userIds) {
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Candidate> rows = systemScope(() -> toCandidates(mapper.selectUsers(ids)));
        // 按入参顺序返回（selectUsers 按 id 排序，业务上更希望「发起人选择顺序」）
        List<Candidate> ordered = new ArrayList<>();
        for (Long id : ids) {
            for (Candidate candidate : rows) {
                if (id.equals(candidate.userId())) {
                    ordered.add(candidate);
                    break;
                }
            }
        }
        return ordered;
    }

    @Override
    public List<Candidate> usersByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return List.of();
        }
        return systemScope(() -> {
            List<Candidate> candidates = toCandidates(mapper.selectUsersByRoleCode(roleCode.trim()));
            if (candidates.isEmpty()) {
                log.debug("角色 {} 下没有在职用户（designated 规则会解析为空候选人并拦截发起）", roleCode);
            }
            return candidates;
        });
    }

    // ================================================================ 内部

    /** 在系统口径下执行一次查询（保存并还原线程上的数据域上下文）。 */
    private <T> T systemScope(Supplier<T> action) {
        DataScopeContext previous = DataScopeContext.current();
        DataScopeContext.set(DataScopeContext.system());
        try {
            return action.get();
        } finally {
            if (previous == null) {
                DataScopeContext.clear();
            } else {
                DataScopeContext.set(previous);
            }
        }
    }

    private static OrgNodeView toView(OrgRow row) {
        return new OrgNodeView(row.getId(), row.getParentId(), row.getOrgType(), row.getName(), row.getPath(),
                row.getDepth(), row.getStatus());
    }

    private static Candidate toView(CandidateRow row) {
        return new Candidate(row.getUserId(), row.getName(), row.getAccount(), row.getEmployeeNo(),
                row.getOrgId(), row.getOrgName(), row.getOrgPath(), row.getCompanyId(), row.getPosition(),
                row.getUserStatus());
    }

    private static List<Candidate> toCandidates(List<CandidateRow> rows) {
        List<Candidate> candidates = new ArrayList<>();
        if (rows == null) {
            return candidates;
        }
        for (CandidateRow row : rows) {
            if (row != null && row.getUserId() != null) {
                candidates.add(toView(row));
            }
        }
        return candidates;
    }
}
