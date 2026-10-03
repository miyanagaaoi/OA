---
uid: 43bb094c
id: oa.integration.contract.export
parent: oa.integration.contract
state: planned
name: {zh: "人工导出归档", en: "Manual Contract Export"}
description:
  zh: >
      MVP 路径：合同审批单审批通过后由集团办/经发部人工导出合同文本归档，不做系统对接。
      
  en: >
      MVP path: approved contract documents are exported manually by the group office or business development for archiving, with no system-to-system integration.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.621Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/contracts/{instance_id}/export"
    description:
      zh: >
          导出已审批通过的合同文本供人工归档。
          
      en: >
          Exports an approved contract for manual archiving.
          
  - protocol: file
    path: "export/contracts/{instance_id}.pdf"
    description:
      zh: >
          导出的合同文本文件。
          
      en: >
          Exported contract file.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.2 合同管理系统集成路径`（§8.2 合同管理系统集成路径）
