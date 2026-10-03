---
uid: 6aa474ce
id: oa.admin.authz.scope.preview
parent: oa.admin.authz.scope
state: planned
name: {zh: "可见范围试算", en: "Visibility Preview"}
description:
  zh: >
      在数据域配置生效前，通过抽样试算该角色可见的单据范围并展示角色×数据域对照表，便于管理员验收。
      
  en: >
      Lets an administrator verify a data-scope configuration before it goes live by simulating which documents a role would see and by showing the role-by-scope matrix.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.237Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/admin/data-scope/preview"
    description:
      zh: >
          按角色试算可见单据范围（抽样）。
          
      en: >
          Simulate the visible documents of a role on a sample.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/data-scope/matrix"
    description:
      zh: >
          角色×数据域对照表。
          
      en: >
          Role by data-scope cross reference table.
          
deps:
  - kind: call
    to: oa.authz.scope
    from_api: "POST /api/v1/admin/data-scope/preview"
    label: {zh: "按数据域口径试算", en: "Simulate the data scope"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
