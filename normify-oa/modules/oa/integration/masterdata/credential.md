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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.249Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
