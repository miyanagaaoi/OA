---
uid: 318cf18c
id: oa.admin.org.bulk.impact
parent: oa.admin.org.bulk
name: {zh: "受影响在途单据预检", en: "In-Flight Impact Preview"}
description:
  zh: >
      批量调整（如公司重组）导入前生成受影响在途单据清单，标注发起人、当前节点与快照审批人，供管理员确认后再执行导入。
      
  en: >
      Before a bulk adjustment such as a reorganisation, produce the list of affected in-flight documents with initiator, current node and snapshot approvers for administrator confirmation.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.427Z"
fingerprint: f14582e3acc613af24e1f05d3533b5ef52f9d412505069347d50e921b1fdb382
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/BulkImportService.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/ImportReport.java"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/admin/bulk-import/impact-preview"
    description:
      zh: >
          生成受影响在途单据清单（dry-run，不落库）。
          
      en: >
          Generates the affected in-flight document list without persisting.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/admin/bulk-import/impact-preview"
    label: {zh: "统计受影响在途实例", en: "Collect in-flight instances"}
---
