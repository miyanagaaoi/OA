---
uid: 2124d96c
id: oa.integration.gateway.authz.write-guard
parent: oa.integration.gateway.authz
state: planned
name: {zh: "外部写入拦截", en: "External Write Guard"}
description:
  zh: >
      一期拒绝一切外部写入尝试，在权限模型成熟前保持网关只读。
      
  en: >
      Rejects every external write attempt in phase one, keeping the gateway read-only until the permission model is mature.
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:00:16.149Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 516
    end_line: 516
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
