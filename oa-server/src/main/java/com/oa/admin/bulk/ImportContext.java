package com.oa.admin.bulk;

import com.oa.common.security.CurrentUser;

/**
 * 一次导入会话的上下文（校验与落库共享）。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #principal()} 调用人（决定 {@link #scope()} 与角色判定）；</li>
 *   <li>{@link #snapshot()} 全库只读索引（组织/账号/角色），在**系统口径**下构建一次；</li>
 *   <li>{@link #scope()} 逐行数据域闸门（分公司管理员 fail-closed）；</li>
 *   <li>{@link #dryRun()} 干跑（preview）时为 {@code true}：不写任何业务表。</li>
 * </ul>
 */
public record ImportContext(
        CurrentUser principal,
        Long operatorId,
        boolean dryRun,
        ImportLookup.Snapshot snapshot,
        ImportScopeGuard scope) {

    /** 是否系统管理员（可导入全量，不受逐行数据域限制）。 */
    public boolean admin() {
        return ImportPermission.isAdmin(principal);
    }
}
