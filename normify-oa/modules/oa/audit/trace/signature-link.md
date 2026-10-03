---
uid: 08e3b566
id: oa.audit.trace.signature-link
parent: oa.audit.trace
state: planned
name: {zh: "轨迹签名挂载", en: "Trail Signature Link"}
description:
  zh: >
      把签名记录挂到对应的轨迹事件上，并回读签名图与验签结果，使轨迹与签名记录一对一可追溯；签名记录本体独立存储、不可删改。
      
  en: >
      Links signature records to their trail events and reads back signature images and verification results, giving one-to-one traceability while signature records stay independently stored and undeletable.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.273Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/audit/trails/{trace_id}/signature"
    description:
      zh: >
          为轨迹事件挂载签名记录。
          
      en: >
          Attaches a signature record to a trail event.
          
  - protocol: http
    method: GET
    path: "/api/v1/audit/trails/{trace_id}/signature"
    description:
      zh: >
          读取轨迹事件对应的签名图与验签结果。
          
      en: >
          Returns the linked signature image and verification result.
          
deps:
  - kind: call
    to: oa.sign.record
    from_api: "POST /api/v1/audit/trails/{trace_id}/signature"
    label: {zh: "关联签名记录", en: "Link signature record"}
  - kind: reference
    to: oa.sign.ca
    from_api: "GET /api/v1/audit/trails/{trace_id}/signature"
    label: {zh: "读取 CA 验签结果", en: "Read CA verification result"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-003`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE sys_thread`（§6. 签名、附件、抄送、消息、审计）
