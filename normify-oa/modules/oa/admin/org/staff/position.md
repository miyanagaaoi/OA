---
uid: 29b1e338
id: oa.admin.org.staff.position
parent: oa.admin.org.staff
state: planned
name: {zh: "一人多岗任职", en: "Multi-Post Assignment"}
description:
  zh: >
      维护一人在多个组织节点的任职记录与主岗标记；主岗决定数据域判定中的归属公司与默认组织节点。
      
  en: >
      Maintain a user's assignments across multiple org nodes and the primary-post flag; the primary post determines the owning company used by data-scope filtering.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.635Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/data-model.md"
    line: 113
    end_line: 126
  - path: "doc/prd-0.1.md"
    line: 155
    end_line: 157
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/users/{user_id}/positions"
    description:
      zh: >
          查询该人员的全部任职记录。
          
      en: >
          List all assignments of the user.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/users/{user_id}/positions"
    description:
      zh: >
          新增任职（一人多岗），可设置主岗。
          
      en: >
          Add an assignment and optionally mark it primary.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/user-positions/{position_id}"
    description:
      zh: >
          删除任职记录（主岗需先改立）。
          
      en: >
          Delete an assignment; primary post changes first.
          
deps:
  - kind: reference
    to: oa.identity.position
    label: {zh: "任职数据支撑数据域归属", en: "Feeds data-scope ownership"}
  - kind: dataflow
    to: oa.identity.position.multi-post
    to_api: "mysql:sys_user_position"
    label: {zh: "写入任职记录", en: "Write post assignment"}
---
