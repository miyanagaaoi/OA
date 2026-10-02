---
uid: 2262d962
id: oa.identity.position.assignment
parent: oa.identity.position
state: planned
name: {zh: "调岗与岗位变更", en: "Post Transfer"}
description:
  zh: >
      调岗/岗位变更处理：更新一人多岗记录与负责人绑定，并发出岗位变更事件；在途单据按发起时快照执行，不因调岗而改派。
      
  en: >
      Handles transfers: updates multi-post records and leader bindings and emits a post-changed event; in-flight documents keep running on their initiation snapshot and are not reassigned by a transfer.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.686Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 234
    end_line: 244
  - path: "doc/data-model.md"
    line: 110
    end_line: 126
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/users/{id}/transfer"
    description:
      zh: >
          发起调岗并同步岗位任职。
          
      en: >
          Transfers a user and syncs posts.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/{id}/assignment-history"
    description:
      zh: >
          查询岗位变更历史。
          
      en: >
          Reads post-change history.
          
  - protocol: kafka
    path: "oa.identity.position.changed"
    description:
      zh: >
          岗位变更事件主题。
          
      en: >
          Post-change event topic.
          
deps:
  - kind: call
    to: oa.identity.position.multi-post
    from_api: "POST /api/v1/identity/users/{id}/transfer"
    to_api: "POST /api/v1/identity/users/{id}/positions"
    label: {zh: "更新任职记录", en: "Update post records"}
  - kind: event
    to: oa.workflow.runtime
    label: {zh: "调岗事件：在途单据按快照不变", en: "Post-change event"}
---
