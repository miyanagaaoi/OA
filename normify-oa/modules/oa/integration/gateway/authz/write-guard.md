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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.745Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
