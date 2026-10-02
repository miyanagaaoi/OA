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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.800Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
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
