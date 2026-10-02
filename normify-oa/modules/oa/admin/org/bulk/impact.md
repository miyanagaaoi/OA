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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.669Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
