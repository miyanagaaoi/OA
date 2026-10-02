---
uid: 04478c06
id: oa.identity.org.import
parent: oa.identity.org
state: planned
name: {zh: "组织批量调整导入", en: "Org Bulk Import"}
description:
  zh: >
      Excel 批量导入与导出组织树；公司重组等批量调整前先给出受影响在途单据清单，管理员确认后才执行，避免快照策略下单据卡死。
  en: >
      Excel bulk import and export of the org tree; before restructuring, the affected in-flight documents are listed and execution requires admin confirmation, so snapshots cannot strand documents.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 234
    end_line: 244
  - path: "doc/prd-0.1.md"
    line: 433
    end_line: 440
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/orgs/import/preview"
    description:
      zh: >
          解析导入文件并给出冲突与受影响在途清单。
      en: >
          Parses the upload and returns conflicts plus affected in-flight documents.
  - protocol: http
    method: POST
    path: "/api/v1/identity/orgs/import"
    description:
      zh: >
          确认后执行批量导入。
      en: >
          Executes the bulk import after confirmation.
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/export"
    description:
      zh: >
          导出组织树。
      en: >
          Exports the org tree.
deps:
  - kind: call
    to: oa.identity.org.node
    from_api: "POST /api/v1/identity/orgs/import"
    to_api: "POST /api/v1/identity/orgs"
    label: {zh: "批量落库组织节点", en: "Persist imported nodes"}
  - kind: call
    to: oa.identity.org.path
    from_api: "POST /api/v1/identity/orgs/import"
    to_api: "POST /api/v1/identity/orgs/{id}/move"
    label: {zh: "重排导入后层级路径", en: "Rebuild paths after import"}
  - kind: dataflow
    to: oa.workflow.runtime
    label: {zh: "导入前给出受影响在途清单", en: "List affected in-flight docs"}
---
