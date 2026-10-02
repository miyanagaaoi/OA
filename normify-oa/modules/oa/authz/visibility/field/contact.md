---
uid: 849bd261
id: oa.authz.visibility.field.contact
parent: oa.authz.visibility.field
state: planned
name: {zh: "联系方式脱敏", en: "Contact Masking"}
description:
  zh: >
      手机号在通讯录中默认脱敏为 138****8888，仅本人与系统管理员可见完整值；对外提供统一的脱敏渲染契约，供通讯录、单据详情与 H5 复用。
      
  en: >
      Phone numbers are masked as 138****8888 in the directory and only the owner or a system admin may see the full value; a single masking contract is exposed for the directory, document detail and H5 surfaces.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.638Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 192
    end_line: 199
  - path: "doc/data-model.md"
    line: 59
    end_line: 83
apis:
  - protocol: rpc
    path: "authz.visibility.mask.phone"
    description:
      zh: >
          按调用者身份返回脱敏或完整手机号。
          
      en: >
          Returns a masked or full phone by caller identity.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/field-policy/contact"
    description:
      zh: >
          读取联系方式脱敏策略。
          
      en: >
          Reads the contact masking policy.
          
deps:
  - kind: call
    to: oa.authz.scope.isolation
    from_api: "rpc:authz.visibility.mask.phone"
    to_api: "rpc:authz.scope.assertAccess"
    label: {zh: "判定本人或系统管理员", en: "Check caller identity"}
---
