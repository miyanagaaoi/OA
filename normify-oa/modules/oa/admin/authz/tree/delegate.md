---
uid: 6772dd9f
id: oa.admin.authz.tree.delegate
parent: oa.admin.authz.tree
state: planned
name: {zh: "逐级分配与再分配限制", en: "Delegation Limits"}
description:
  zh: >
      控制谁可以向谁分配权限：分公司流程管理员只获得本公司范围内的分配权，并被明确禁止再向下分配。
      
  en: >
      Controls who may assign permissions to whom: branch process admins receive a company-scoped delegation range and are explicitly barred from re-delegating further down.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.614Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 165
    end_line: 166
  - path: "doc/prd-0.1.md"
    line: 166
    end_line: 166
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/delegations/{role_id}"
    description:
      zh: >
          查询该角色的分配范围。
          
      en: >
          Read the delegation range configured for a role.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/delegations/{role_id}"
    description:
      zh: >
          设置可分配的组织节点范围与是否可再分配。
          
      en: >
          Set delegatable org nodes and re-delegation flag.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/delegations/{role_id}/check"
    description:
      zh: >
          校验操作者是否越权分配。
          
      en: >
          Verify the operator is not delegating beyond scope.
          
deps:
  - kind: call
    to: oa.admin.boundary
    from_api: "POST /api/v1/admin/delegations/{role_id}/check"
    label: {zh: "再分配边界校验", en: "Boundary on re-delegation"}
---
