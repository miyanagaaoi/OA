package com.oa.common.security;

import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeType;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前登录人（认证主体），随 {@link DataScopeContext} 一起线程绑定。
 *
 * <p>这是**鉴权阶段**的产物：只承载身份与权限快照，不承载业务数据；
 * 字段与 {@code sys_user} / {@code sys_role} 的列一一对应，便于 {@code /api/v1/auth/me} 直接输出。
 *
 * @param id                 用户 id（{@code sys_user.id}）
 * @param account            登录账号
 * @param name               姓名
 * @param employeeNo         工号（水印用）
 * @param orgId              主归属组织节点（{@code sys_user.org_id}）
 * @param companyId          归属公司（数据域判定用）
 * @param roleCodes          角色编码集合（{@code sys_role.code}）
 * @param dataScopes         数据域集合（多角色并集）
 * @param mustChangePassword 是否必须修改口令（首登强制改密）
 */
public record CurrentUser(
        Long id,
        String account,
        String name,
        String employeeNo,
        Long orgId,
        Long companyId,
        Set<String> roleCodes,
        Set<DataScopeType> dataScopes,
        boolean mustChangePassword
) implements Serializable {

    public CurrentUser {
        roleCodes = roleCodes == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
        dataScopes = dataScopes == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(dataScopes));
    }

    public static CurrentUser of(Long id, String account, String name, String employeeNo,
                                 Long orgId, Long companyId,
                                 Set<String> roleCodes, Set<DataScopeType> dataScopes,
                                 boolean mustChangePassword) {
        return new CurrentUser(id, account, name, employeeNo, orgId, companyId, roleCodes, dataScopes, mustChangePassword);
    }

    /** 是否具备指定角色（用于导出权限、菜单级判断；数据域过滤一律不靠角色名硬编码）。 */
    public boolean hasRole(String roleCode) {
        return roleCode != null && roleCodes.contains(roleCode);
    }
}
