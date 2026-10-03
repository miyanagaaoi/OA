---
uid: 42cf1e88
id: oa.integration.masterdata.credential
parent: oa.integration.masterdata
name: {zh: "开放平台凭证", en: "Open API Credentials"}
description:
  zh: >
      为下游系统签发、轮换与吊销只读调用凭证，每次调用写入调用日志供审计。
      
  en: >
      Issuing, rotating and revoking read-only API credentials for downstream systems, with every call logged for audit.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.077Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
