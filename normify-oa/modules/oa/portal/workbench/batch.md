---
uid: 0db9d1cd
id: oa.portal.workbench.batch
parent: oa.portal.workbench
state: planned
name: {zh: "批量操作", en: "Batch Operations"}
description:
  zh: >
      勾选多行后的批量动作：批量同意（逐条校验意见与签名要求）、批量转办、勾选导出（仅系统管理员）；破坏性批量动作二次确认并列出将被处理的对象清单；逐条回执，单条失败不回滚其余。
      
  en: >
      Batch actions after selecting rows: batch approve (per-row opinion and signature checks), batch transfer, and export of the selection (system administrator only); destructive batches require a second confirmation listing the affected objects; results come back row by row and one failure does not roll back the rest.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.289Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 910
    end_line: 910
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/workbench/tasks/batch-approve"
    description:
      zh: >
          批量同意勾选任务。
          
      en: >
          Batch approve the selected tasks.
          
  - protocol: http
    method: POST
    path: "/api/v1/portal/workbench/tasks/batch-transfer"
    description:
      zh: >
          批量转办勾选任务。
          
      en: >
          Batch transfer the selected tasks.
          
  - protocol: file
    path: "export/workbench-selected.csv"
    description:
      zh: >
          勾选单据导出（仅系统管理员）。
          
      en: >
          Export of the selected documents (system administrator only).
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/portal/workbench/tasks/batch-approve"
    label: {zh: "批量提交审批动作", en: "Submit batch approvals"}
  - kind: call
    to: oa.authz.rbac
    from_api: "file:export/workbench-selected.csv"
    label: {zh: "导出权限校验", en: "Export permission check"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
