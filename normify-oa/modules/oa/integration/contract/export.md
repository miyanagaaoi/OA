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
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.335Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 522
    end_line: 523
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
