---
uid: 363dd0ea
id: oa.archive.search.preview
parent: oa.archive.search
state: planned
name: {zh: "历史预览", en: "History Preview"}
description:
  zh: >
      归档单据的只读详情预览：表单字段、附件、审批轨迹与签名图只读渲染，复用在线详情的展示口径但不提供任何操作按钮。
      
  en: >
      Read-only preview of an archived document: form fields, attachments, approval trail and signature images rendered with the live detail conventions but no action buttons.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.543Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 540
    end_line: 540
  - path: "doc/data-model.md"
    line: 824
    end_line: 824
apis:
  - protocol: http
    method: GET
    path: "/api/v1/archive/documents/{biz_no}/preview"
    description:
      zh: >
          归档单据的只读详情预览。
          
      en: >
          Read-only preview of an archived document.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/documents/{biz_no}/trail"
    description:
      zh: >
          归档单据的历史审批轨迹。
          
      en: >
          Read-only approval trail of an archived document.
          
deps:
  - kind: call
    to: oa.portal.detail
    from_api: "GET /api/v1/archive/documents/{biz_no}/preview"
    label: {zh: "复用只读详情渲染", en: "Reuse read-only detail view"}
  - kind: call
    to: oa.audit.trace.timeline
    from_api: "GET /api/v1/archive/documents/{biz_no}/trail"
    to_api: "GET /api/v1/instances/{instance_id}/trail"
    label: {zh: "历史审批轨迹展示", en: "Show historical trail"}
  - kind: reference
    to: oa.form.print
    from_api: "GET /api/v1/archive/documents/{biz_no}/preview"
    label: {zh: "历史单据打印", en: "Print archived document"}
---
