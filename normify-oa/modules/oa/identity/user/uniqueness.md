---
uid: 2f9d6c04
id: oa.identity.user.uniqueness
parent: oa.identity.user
name: {zh: "账号与工号唯一性判重（系统口径）", en: "Account & Employee-No Uniqueness (System Scope)"}
description:
  zh: >
      人员新增/修改时的账号与工号判重：唯一性是**全局约束**（uk_sys_user_account），与调用人数据域无关，因此走**系统口径**专用语句（countByAccountSystem / countByEmployeeNoSystem）而不是数据域过滤语句；命中重复返回 409 / 40902，文案指向 import-spec E-USER-002 / E-USER-015，不再落到数据库唯一键上，也避免工号（**无库唯一键**）被静默写入。两条判重语句只读计数、不返回行数据，已列入 oa.scope.exempt-statement-ids 窄豁免——数据域读取限制一行未动。
      
  en: >
      Account and employee-number uniqueness checks use system-scope statements (countByAccountSystem / countByEmployeeNoSystem), because uniqueness is a global constraint (uk_sys_user_account) unrelated to the caller's data scope. A hit returns 409 / 40902 citing import-spec E-USER-002 / E-USER-015, so employee numbers (which have no unique key) can no longer be silently duplicated. Both statements read only a COUNT; data-scope read limits are unchanged.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.670Z"
fingerprint: cc417de0db1956a860697daa53b1b8bff5d6c4c0e7ccf5e8dadbd6a635ccacf0
source:
  - path: "oa-server/src/main/java/com/oa/identity/app/UserService.java"
  - path: "oa-server/src/main/java/com/oa/identity/infra/SysUserMapper.java"
apis:
  - protocol: rpc
    path: "identity.user.assertAccountUnique"
    description:
      zh: >
          账号全库唯一断言（系统口径，不分数据域）。
          
      en: >
          Asserts the account is globally unique (system scope, ignores data scope).
          
  - protocol: rpc
    path: "identity.user.assertEmployeeNoUnique"
    description:
      zh: >
          工号全库唯一断言（系统口径；工号无库唯一键，必须在此拦截）。
          
      en: >
          Asserts the employee number is globally unique (system scope; there is no database unique key for it).
          
deps:
  - kind: reference
    to: oa.identity.user.profile
    from_api: "rpc:identity.user.assertAccountUnique"
    to_api: "POST /api/v1/identity/users"
    label: {zh: "服务于人员档案新增/修改", en: "Backs profile create/update"}
---
