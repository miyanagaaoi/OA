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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.648Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 428
    end_line: 428
  - path: "doc/data-model.md"
    line: 637
    end_line: 637
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
