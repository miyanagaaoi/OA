---
uid: 1efd065f
id: oa.identity.position.leader-lookup
parent: oa.identity.position
name: {zh: "负责人查询与上溯", en: "Leader Lookup & Escalation"}
description:
  zh: >
      按组织节点返回负责人候选人：先取本科室负责人，科室未设负责人时上溯取所属部门负责人；候选人为空时由发起前拦截拒绝，不允许静默跳过。
      
  en: >
      Returns leader candidates for an org node: the section's leaders first, escalating to the parent department when none is set; an empty candidate set blocks initiation rather than being silently skipped.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.602Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 204
    end_line: 216
  - path: "doc/prd-0.1.md"
    line: 226
    end_line: 232
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/{id}/leader-candidates"
    description:
      zh: >
          返回节点（含上溯）的负责人候选人集合。
          
      en: >
          Returns leader candidates including escalation.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/{id}/leader-of"
    description:
      zh: >
          列出该用户担任负责人的组织节点。
          
      en: >
          Lists the org nodes a user leads.
          
  - protocol: rpc
    path: "identity.position.resolveLeader"
    description:
      zh: >
          供流程侧调用的候选人解析 RPC。
          
      en: >
          RPC used by the workflow side to resolve candidates.
          
deps:
  - kind: call
    to: oa.identity.position.leader-bind
    from_api: "GET /api/v1/identity/orgs/{id}/leader-candidates"
    to_api: "GET /api/v1/identity/orgs/{id}/leaders"
    label: {zh: "读取节点负责人", en: "Read bound leaders"}
  - kind: call
    to: oa.identity.org.path
    from_api: "GET /api/v1/identity/orgs/{id}/leader-candidates"
    to_api: "GET /api/v1/identity/orgs/{id}/ancestors"
    label: {zh: "科室缺位时上溯部门", en: "Escalate to parent dept"}
  - kind: call
    to: oa.identity.position.multi-post
    from_api: "GET /api/v1/identity/orgs/{id}/leader-candidates"
    to_api: "GET /api/v1/identity/users/{id}/positions"
    label: {zh: "合并一人多岗候选人", en: "Merge multi-post holders"}
---
