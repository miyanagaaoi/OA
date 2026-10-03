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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.744Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
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
