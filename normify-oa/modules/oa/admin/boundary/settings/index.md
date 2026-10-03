---
uid: d2ba4378
id: oa.admin.boundary.settings
parent: oa.admin.boundary
state: planned
name: {zh: "系统参数与保留期", en: "System Settings & Retention"}
description:
  zh: >
      系统管理员可调的系统级参数：会话与登录策略、审计与日志保留期；参数均带不可越过的硬下限（审计与轨迹保留不得短于 10 年）。
      
  en: >
      System-wide parameters the super-admin may tune: session and login policy plus audit and log retention, with hard floors that cannot be crossed (audit and trace retention never shorter than ten years).
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.114Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-007`（§6.9 审计日志）
