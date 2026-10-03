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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.729Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 368
    end_line: 368
  - path: "doc/data-model.md"
    line: 557
    end_line: 558
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
