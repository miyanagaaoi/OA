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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.547Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
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
