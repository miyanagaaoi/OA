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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.680Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 540
    end_line: 540
  - path: "doc/data-model.md"
    line: 794
    end_line: 794
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
