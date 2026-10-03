---
uid: 46c4bc85
id: oa.sign.record.query
parent: oa.sign.record
state: planned
name: {zh: "签名记录查询与展示", en: "Signature Record Query"}
description:
  zh: >
      查询单据的全部签名记录（含历史版本、设备指纹、IP、签署时间）与单条详情，并提供鉴权后的签名图下载；签名图禁止直链，越权访问返回无权限而非空白页。
      
  en: >
      Queries all signature records of a document (including historical versions, device fingerprints, IP and signing times) plus single-record detail, and serves the image behind an authenticated download.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.793Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/instances/{instance_id}/signatures"
    description:
      zh: >
          单据签名记录列表（含历史版本）。
          
      en: >
          Lists signature records of a document including historical versions.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/records/{id}"
    description:
      zh: >
          签名记录详情（含取证信息）。
          
      en: >
          Reads one signature record with its forensic evidence.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/records/{id}/image"
    description:
      zh: >
          鉴权下载签名图（禁止直链）。
          
      en: >
          Authenticated download of the signature image, never a public link.
          
deps:
  - kind: reference
    to: oa.sign.record.append
    to_api: "mysql:flow_signature"
    label: {zh: "读取只追加的签名记录", en: "Read append-only records"}
  - kind: reference
    to: oa.authz.scope
    label: {zh: "按数据域限制可见范围", en: "Limited by data scope"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-004`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
