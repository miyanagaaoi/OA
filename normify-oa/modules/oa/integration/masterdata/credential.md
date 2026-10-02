---
uid: 42cf1e88
id: oa.integration.masterdata.credential
parent: oa.integration.masterdata
state: planned
name: {zh: "开放平台凭证", en: "Open API Credentials"}
description:
  zh: >
      为下游系统签发、轮换与吊销只读调用凭证，每次调用写入调用日志供审计。
      
  en: >
      Issuing, rotating and revoking read-only API credentials for downstream systems, with every call logged for audit.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.698Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 511
    end_line: 516
apis:
  - protocol: http
    method: POST
    path: "/api/v1/open/credentials"
    description:
      zh: >
          签发只读调用凭证。
          
      en: >
          Issues a read-only API credential.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/open/credentials/{id}"
    description:
      zh: >
          吊销调用凭证。
          
      en: >
          Revokes a credential.
          
  - protocol: file
    path: "config/open-api-clients.yml"
    description:
      zh: >
          开放平台调用方白名单配置。
          
      en: >
          Whitelist of open-API clients.
          
---
