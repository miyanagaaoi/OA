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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.667Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
