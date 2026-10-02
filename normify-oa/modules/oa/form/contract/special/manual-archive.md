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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.711Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
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
