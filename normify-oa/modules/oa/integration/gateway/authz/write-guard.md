---
uid: 2124d96c
id: oa.integration.gateway.authz.write-guard
parent: oa.integration.gateway.authz
name: {zh: "外部写入拦截", en: "External Write Guard"}
description:
  zh: >
      一期拒绝一切外部写入尝试，在权限模型成熟前保持网关只读。
      
  en: >
      Rejects every external write attempt in phase one, keeping the gateway read-only until the permission model is mature.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.074Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/gateway/write-guard"
    description:
      zh: >
          一期拒绝来自系统外部的非 GET 请求。
          
      en: >
          Rejects non-GET methods coming from outside the system in phase one.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
