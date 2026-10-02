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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.695Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
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
