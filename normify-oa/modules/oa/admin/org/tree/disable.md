---
uid: 21c71725
id: oa.admin.org.tree.disable
parent: oa.admin.org.tree
state: planned
name: {zh: "组织停用闸门", en: "Org Disable Gate"}
description:
  zh: >
      停用组织节点前统计并展示该节点全部在途单据，确认后方可停用；停用后该节点不可再作为发起者归属节点，重新启用需再次校验。
      
  en: >
      Before disabling an org node, count and display all in-flight documents of that node and require confirmation; a disabled node cannot own new initiators, and re-enabling re-runs the check.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.637Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 240
    end_line: 240
  - path: "doc/prd-0.1.md"
    line: 244
    end_line: 244
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/orgs/{org_id}/inflight-check"
    description:
      zh: >
          统计该节点在途单据数量（停用前置校验）。
          
      en: >
          Count in-flight documents of the node as a pre-disable check.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/orgs/{org_id}/disable"
    description:
      zh: >
          确认后停用组织节点。
          
      en: >
          Disable the org node after confirmation.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/orgs/{org_id}/enable"
    description:
      zh: >
          重新启用组织节点。
          
      en: >
          Re-enable the org node.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "GET /api/v1/admin/orgs/{org_id}/inflight-check"
    label: {zh: "查询在途流程实例", en: "Query in-flight instances"}
---
