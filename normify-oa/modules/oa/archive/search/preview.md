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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.147Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-010`（§第9章 非功能需求）
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
