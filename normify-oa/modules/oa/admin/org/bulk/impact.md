---
uid: 318cf18c
id: oa.admin.org.bulk.impact
parent: oa.admin.org.bulk
state: planned
name: {zh: "受影响在途单据预检", en: "In-Flight Impact Preview"}
description:
  zh: >
      批量调整（如公司重组）导入前生成受影响在途单据清单，标注发起人、当前节点与快照审批人，供管理员确认后再执行导入。
      
  en: >
      Before a bulk adjustment such as a reorganisation, produce the list of affected in-flight documents with initiator, current node and snapshot approvers for administrator confirmation.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.603Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 242
    end_line: 242
  - path: "doc/prd-0.1.md"
    line: 202
    end_line: 202
apis:
  - protocol: http
    method: POST
    path: "/api/v1/admin/bulk-import/impact-preview"
    description:
      zh: >
          生成受影响在途单据清单（不落库）。
          
      en: >
          Generate the affected in-flight document list without persisting.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/bulk-import/{batch_id}/impact"
    description:
      zh: >
          查询该批次的受影响清单与快照审批人。
          
      en: >
          Fetch the batch's affected list and snapshot approvers.
          
  - protocol: file
    path: "export/bulk-impact/{batch_id}.xlsx"
    description:
      zh: >
          受影响在途单据清单导出件。
          
      en: >
          Exported affected in-flight document list.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/admin/bulk-import/impact-preview"
    label: {zh: "统计受影响在途实例", en: "Collect in-flight instances"}
---
