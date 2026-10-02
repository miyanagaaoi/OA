package com.oa.authz.app;

import com.oa.authz.domain.SysRole;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 把「当前登录人」装配成 {@link AuthorizationPolicy.Operator}（鉴权输入的唯一来源）。
 *
 * <p>只在这里做一次上下文读取，保证所有 authz 服务用的是同一份操作人快照；
 * 参数校验（缺失即 401）也集中在此。
 *
 * <p>性能：只有系统管理员与分公司流程管理员才会去查「自身有效权限 id」
 * （越权再授权判定需要它）；其余角色的鉴权在角色码层面就会被 403 拒绝，不必多查一次库。
 */
@Component
public class AuthzOperatorProvider {

    private final EffectivePermissionService effectivePermissionService;

    public AuthzOperatorProvider(EffectivePermissionService effectivePermissionService) {
        this.effectivePermissionService = effectivePermissionService;
    }

    /** 当前操作人（未认证 → 401）。 */
    public AuthorizationPolicy.Operator current() {
        CurrentUser principal = currentUser();
        Set<Long> permissionIds = Set.of();
        Set<String> roleCodes = principal.roleCodes();
        boolean needsPermissions = roleCodes.contains(AuthorizationPolicy.ROLE_ADMIN)
                || roleCodes.contains(AuthorizationPolicy.ROLE_COMPANY_ADMIN);
        if (needsPermissions) {
            permissionIds = effectivePermissionService.permissionIds(principal.id());
        }
        return AuthorizationPolicy.Operator.of(principal.id(), roleCodes, permissionIds, principal.companyId());
    }

    /** 当前登录人（未认证 → 401）。 */
    public CurrentUser currentUser() {
        CurrentUser principal = DataScopeContext.require().getPrincipal();
        if (principal == null || principal.id() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    /** 当前登录人所属公司（越权分配判定用）。 */
    public Long currentCompanyId() {
        return currentUser().companyId();
    }

    /** 目标角色的 {@code role_scope}（空值按集团级从严处理）。 */
    public static String roleScopeOrGroup(SysRole role) {
        return role == null || role.getRoleScope() == null ? SysRole.SCOPE_GROUP : role.getRoleScope();
    }
}
