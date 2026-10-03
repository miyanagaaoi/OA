---
uid: 79910ada
id: oa.form.contract.special.manual-archive
parent: oa.form.contract.special
name: {zh: "人工导出归档", en: "Manual Export Archiving"}
description:
  zh: >
      一期不做合同台账：审批通过后由集团办/经发部人工导出归档（PRD 8.2），系统只提供导出口径与字段完整性保证，并保留归口为财务部的记录。
      
  en: >
      Phase one builds no contract ledger: after approval the group office exports the document manually for archiving (PRD 8.2); the system only offers the export view with complete fields and keeps the finance central-ownership record.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.260Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
