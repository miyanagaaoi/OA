---
uid: 29a35127
id: oa.platform.security.crypto.encrypt
parent: oa.platform.security.crypto
state: planned
name: {zh: "字段级加密", en: "Field Encryption"}
description:
  zh: >
      手机号等个人信息的字段级加密；默认返回脱敏值，仅本人与系统管理员可取完整值。
      
  en: >
      Field-level encryption for phone numbers and other PII, with masked values returned by default and full values only for the owner and administrators.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.716Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 196
    end_line: 196
apis:
  - protocol: file
    path: "config/crypto/field-encryption.yml"
    description:
      zh: >
          哪些字段落库加密、响应中如何脱敏的配置。
          
      en: >
          Which fields are encrypted at rest and how they are masked in responses.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/crypto/fields"
    description:
      zh: >
          查询已加密字段及其当前脱敏方式。
          
      en: >
          Lists encrypted fields and their current masking mode.
          
---
