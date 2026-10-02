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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.618Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
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
