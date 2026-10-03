---
uid: 353b9efa
id: oa.archive.search.lookup
parent: oa.archive.search
state: planned
name: {zh: "按单号检索", en: "Lookup by Document No"}
description:
  zh: >
      依据业务单号（OA-{四位年}-{六位流水}）精确定位归档单据，并支持按年度、事项类别分页浏览历史单据列表。
      
  en: >
      Locates an archived document precisely by its business number (OA-{year}-{seq}) and browses historical documents by year and matter category.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.265Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/archive/documents/{biz_no}"
    description:
      zh: >
          按业务单号定位归档单据。
          
      en: >
          Locates an archived document by business number.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/documents"
    description:
      zh: >
          按年度与事项类别分页浏览历史单据。
          
      en: >
          Browses archived documents by year and category.
          
deps:
  - kind: call
    to: oa.archive.search.router
    from_api: "GET /api/v1/archive/documents/{biz_no}"
    to_api: "GET /api/v1/archive/search"
    label: {zh: "经统一入口路由", en: "Route via unified entry"}
  - kind: reference
    to: oa.workflow.runtime
    from_api: "GET /api/v1/archive/documents"
    label: {zh: "一单一号唯一性口径", en: "Unique document number rule"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-010`（§第9章 非功能需求）
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
