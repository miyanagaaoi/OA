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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.748Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
