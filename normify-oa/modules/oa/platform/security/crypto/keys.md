---
uid: 5064ac25
id: oa.platform.security.crypto.keys
parent: oa.platform.security.crypto
state: planned
name: {zh: "密钥管理", en: "Key Management"}
description:
  zh: >
      密钥托管与轮换：API 不返回密钥本体，轮换显式触发且状态可审计。
      
  en: >
      Key custody and rotation: keys are never returned by the API, rotation is triggered explicitly and its status is auditable.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.733Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 535
    end_line: 535
apis:
  - protocol: http
    method: POST
    path: "/api/v1/admin/keys/rotate"
    description:
      zh: >
          触发加密字段的密钥轮换。
          
      en: >
          Triggers a key rotation for encrypted fields.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/keys"
    description:
      zh: >
          查询密钥状态（不返回密钥本体）。
          
      en: >
          Reads key status without exposing key material.
          
  - protocol: file
    path: "config/crypto/key-rotation.yml"
    description:
      zh: >
          轮换周期与密钥版本保留策略。
          
      en: >
          Rotation cadence and key-version retention policy.
          
---
