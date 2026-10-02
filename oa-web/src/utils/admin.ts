/**
 * oa-web · 身份域管理权限口径
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.2（权限模型）、5.3（字段级限制：手机号仅本人与系统管理员可见）、
 *     6.10 REQ-ADMIN-006（系统管理员兜底权限边界，所有操作留痕）
 *   · `doc/import-spec.md` §9.2（**主数据导出仅系统管理员**，T-11 定稿）、
 *     §7.4（影响程度=高 且未确认 → 阻断，确认动作写 sys_log）
 *   · `doc/prd-0.1.md` 5.5「强制继续」唯一例外（2026-10-02 裁定：仅系统管理员 + 必填原因 + 双留痕）
 *   · `DESIGN.md` Agent Usage Rules 第 6 条「权限不可见优于不可用」
 *
 * 口径：入口与按钮**不渲染**（而非置灰）；服务端（`ForceReasonPolicy` / 数据域拦截器）仍是最终裁决方，
 * 本文件只决定「可不可见」，不承担鉴权。
 */
import type { useUserStore } from '@/stores/user'

type UserStore = ReturnType<typeof useUserStore>

/**
 * 系统管理员角色码（`doc/data-model.md` 3.1 `sys_role.code` 的权威取值）。
 *
 * <p>为什么需要它：`GET /api/v1/auth/me` 目前只下发 `roleCodes`（`permissions` / `isSuperAdmin`
 * 要等 **1.4 角色与权限树** 落地后才由 `sys_role_permission` 派生）。若只看权限码，
 * 真实后端下管理入口会**恒为 false**——即「功能好了但按钮永远不出现」。
 * 因此这里以角色码兜底；1.4 完成后应改为「权限码命中 或 系统管理员角色」并保留该兜底。
 */
export const SYSTEM_ADMIN_ROLE = 'admin'

/** 身份域权限码（后端权限字典）；服务端仍是最终裁决方 */
export const IDENTITY_PERMISSION = {
  /** 组织架构与负责人维护 */
  orgManage: 'admin.org.manage',
  /** 人员与岗位维护 */
  userManage: 'admin.user.manage',
  /** 主数据导出：仅系统管理员（import-spec §9.2） */
  userExport: 'admin.user.export',
  /** 危险操作「强制继续」：影响清单非空时绕过阻断，必须留痕 */
  forceChange: 'admin.identity.force',
} as const

/**
 * 是否系统管理员：服务端兜底能力（`isSuperAdmin`）或角色码命中。
 * 两者取并集——前者代表服务端明确授予的兜底能力，后者是 1.4 落地前的过渡判据。
 */
export function isSystemAdmin(store: UserStore): boolean {
  return store.isSuperAdmin || store.roles.some((role) => role.roleCode === SYSTEM_ADMIN_ROLE)
}

/** 组织架构入口：系统管理员，或具备身份域管理权限 */
export function canManageOrg(store: UserStore): boolean {
  return isSystemAdmin(store) || store.hasPermission(IDENTITY_PERMISSION.orgManage)
}

export function canManageUser(store: UserStore): boolean {
  return isSystemAdmin(store) || store.hasPermission(IDENTITY_PERMISSION.userManage)
}

/** 侧栏「管理后台」分组是否需要渲染 */
export function canEnterAdmin(store: UserStore): boolean {
  return canManageOrg(store) || canManageUser(store)
}

/**
 * 导出入口是否可见。
 * import-spec §9.2 T-11：主数据（组织/人员/负责人/岗位/角色分配）导出**仅系统管理员**；
 * 单据与金额类导出才适用「系统管理员与财务角色」，两类分组治理、互不覆盖。
 */
export function canExportMasterData(store: UserStore): boolean {
  return isSystemAdmin(store) || store.hasPermission(IDENTITY_PERMISSION.userExport)
}

/**
 * 是否可对「在途/待办非零」的阻断执行强制继续。
 *
 * <p>强制继续必须**同时**满足：系统管理员（服务端 `ForceReasonPolicy` 同口径）+ 填写原因
 * （原因随请求提交并写入 `sys_log`，同时产生运行日志 `log.warn`）。
 * 注意：满足本判据不等于服务端一定放行——最终由服务端裁决，前端只负责可见性与必填校验。
 */
export function canForceChange(store: UserStore): boolean {
  return isSystemAdmin(store) || store.hasPermission(IDENTITY_PERMISSION.forceChange)
}
