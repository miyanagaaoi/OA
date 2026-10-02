package com.oa.authz.app;

import com.oa.authz.infra.DataScopeMapper;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeProvider;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 数据域解析（doc/tech-design.md §5.3 第 1 步）：从 {@code sys_user} + {@code sys_role} +
 * {@code sys_user_role} + {@code sys_role_category} + {@code sys_org} 装载 {@link DataScopeContext}。
 *
 * <p>合并规则：
 * <ul>
 *   <li>多角色数据域**取并集**（SQL 层按并集 OR 拼装），最宽口径见 {@link DataScopeType#widest};</li>
 *   <li>无任何角色时保守回落到 {@code self}（宁可少看，不可越权）；</li>
 *   <li>{@code sys_user_role.scope_org_id} 记入 {@code roleScopeOrgIds}，供「角色仅在指定组织范围内生效」的
 *       细分场景使用（阶段 1 先把值带到上下文，SQL 细化在阶段 2 与越权用例一起落）；</li>
 *   <li>归口部门（集团财务部）id/路径来自配置，缺省按 {@code oa.scope.finance-dept-name} 兜底查询。</li>
 * </ul>
 */
@Service
public class DataScopeResolver implements DataScopeProvider {

    private static final Logger log = LoggerFactory.getLogger(DataScopeResolver.class);

    private final DataScopeMapper dataScopeMapper;
    private final OaProperties properties;

    public DataScopeResolver(DataScopeMapper dataScopeMapper, OaProperties properties) {
        this.dataScopeMapper = dataScopeMapper;
        this.properties = properties;
    }

    @Override
    public DataScopeContext resolve(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        DataScopeMapper.UserBriefRow user = dataScopeMapper.selectUserBrief(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录人不存在或已删除");
        }
        if (user.getStatus() != null && !"active".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        List<DataScopeMapper.UserRoleRow> roles = dataScopeMapper.selectRoles(userId);
        Set<String> roleCodes = new LinkedHashSet<>();
        Set<DataScopeType> scopes = new LinkedHashSet<>();
        Set<Long> scopeOrgIds = new LinkedHashSet<>();
        for (DataScopeMapper.UserRoleRow role : roles) {
            if (role.getCode() != null) {
                roleCodes.add(role.getCode());
            }
            DataScopeType.of(role.getDataScope()).ifPresentOrElse(scopes::add, () -> {
                if (role.getDataScope() != null) {
                    log.warn("角色 {} 的 data_scope={} 无法识别，已忽略", role.getCode(), role.getDataScope());
                }
            });
            if (role.getScopeOrgId() != null) {
                scopeOrgIds.add(role.getScopeOrgId());
            }
        }
        if (scopes.isEmpty()) {
            scopes.add(DataScopeType.SELF);
        }

        Set<String> categories = new LinkedHashSet<>(dataScopeMapper.selectCategories(userId));

        String deptPathPrefix = null;
        if (user.getOrgId() != null) {
            DataScopeMapper.OrgRow org = dataScopeMapper.selectOrg(user.getOrgId());
            if (org != null) {
                deptPathPrefix = org.getPath();
            }
        }

        OaProperties.Scope scopeConfig = properties.getScope();
        Long financeDeptId = scopeConfig.getFinanceDeptId();
        if (financeDeptId == null && scopeConfig.getFinanceDeptName() != null) {
            financeDeptId = dataScopeMapper.selectOrgIdByName(scopeConfig.getFinanceDeptName());
        }
        String financeDeptPath = scopeConfig.getFinanceDeptPath();
        if ((financeDeptPath == null || financeDeptPath.isBlank()) && financeDeptId != null) {
            DataScopeMapper.OrgRow financeOrg = dataScopeMapper.selectOrg(financeDeptId);
            if (financeOrg != null) {
                financeDeptPath = financeOrg.getPath();
            }
        }
        if (scopes.contains(DataScopeType.GROUP_CATEGORY) && (financeDeptId == null || financeDeptPath == null)) {
            log.warn("用户 {} 具备 group_category 口径，但归口部门（{}）未解析到 id/路径，"
                            + "该口径将只保留「归口类别 + 涉及费用事项单」两路；请检查 oa.scope.finance-dept-* 配置",
                    userId, scopeConfig.getFinanceDeptName());
        }

        boolean mustChangePassword = user.getLastLoginAt() == null;
        CurrentUser principal = CurrentUser.of(user.getId(), user.getAccount(), user.getName(), user.getEmployeeNo(),
                user.getOrgId(), user.getCompanyId(), roleCodes, scopes, mustChangePassword);

        return DataScopeContext.builder()
                .principal(principal)
                .roleCodes(roleCodes)
                .scopes(scopes)
                .roleScopeOrgIds(scopeOrgIds)
                .categories(categories)
                .deptPathPrefix(deptPathPrefix)
                .financeDeptPathPrefix(financeDeptPath)
                .primaryOrgId(user.getOrgId())
                .companyId(user.getCompanyId())
                .financeDeptId(financeDeptId)
                .financeOwnerDeptBranchEnabled(scopeConfig.isFinanceOwnerDeptBranchEnabled())
                .build();
    }
}
