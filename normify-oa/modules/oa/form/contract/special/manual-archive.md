---
uid: 79910ada
id: oa.form.contract.special.manual-archive
parent: oa.form.contract.special
state: planned
name: {zh: "人工导出归档", en: "Manual Export Archiving"}
description:
  zh: >
      一期不做合同台账：审批通过后由集团办/经发部人工导出归档（PRD 8.2），系统只提供导出口径与字段完整性保证，并保留归口为财务部的记录。
  en: >
      Phase one builds no contract ledger: after approval the group office exports the document manually for archiving (PRD 8.2); the system only offers the export view with complete fields and keeps the finance central-ownership record.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/forms.md"
    line: 144
    end_line: 147
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/instances/{instance_id}/export"
    description:
      zh: >
          导出合同单据用于人工归档。
      en: >
          Exports a contract document for manual archiving.
deps:
  - kind: reference
    to: oa.archive
    from_api: "GET /api/v1/forms/contract/instances/{instance_id}/export"
    label: {zh: "人工导出归档", en: "Manual export archiving"}
---
