package com.oa.admin.bulk;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import java.util.List;

/**
 * 导入权限（{@code import-spec} 的「高危写操作」口径 + REQ-ADMIN-001）。
 *
 * <p>判定：<b>系统管理员（admin）或分公司流程管理员（company_admin）</b>才可调用五类导入。
 * 分公司管理员仍受**逐行数据域**约束（见 {@link ImportScopeGuard}，fail-closed），
 * 因此「有权限」与「能改哪一行」是两件事，本类只管前者。
 */
public final class ImportPermission {

    /** 允许调用批量导入的角色码（{@code sys_role.code}）。 */
    public static final List<String> IMPORT_ROLES = List.of("admin", "company_admin");

    private ImportPermission() {
    }

    public static void require(CurrentUser principal) {
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        for (String role : IMPORT_ROLES) {
            if (principal.hasRole(role)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "批量导入属高危写操作：仅系统管理员或分公司流程管理员可调用（REQ-ADMIN-001 / import-spec §2）");
    }

    public static boolean isAdmin(CurrentUser principal) {
        return principal != null && principal.hasRole("admin");
    }
}
