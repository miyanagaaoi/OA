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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.629Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
