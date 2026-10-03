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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.256Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-002`（§5.5 组织与人员变更的处理）
