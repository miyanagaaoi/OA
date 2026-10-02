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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.615Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 169
    end_line: 183
  - path: "doc/data-model.md"
    line: 722
    end_line: 738
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
