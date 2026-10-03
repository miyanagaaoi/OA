---
uid: 1c6f6dfa
id: oa.sign.preset.default
parent: oa.sign.preset
name: {zh: "默认签名切换", en: "Default Preset Signature"}
description:
  zh: >
      在用户多张预存签名中指定默认签名（sys_user_signature.is_default，同一用户仅一张默认为真）；审批签名弹窗默认选中该签名，一键调用。
      
  en: >
      Marks one preset signature as the default per user (is_default is exclusive) so the approval dialog preselects it for one-tap use.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.353Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: PUT
    path: "/api/v1/sign/presets/{id}/default"
    description:
      zh: >
          把某张预存签名设为默认签名。
          
      en: >
          Marks a preset signature as the default one.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/presets/default"
    description:
      zh: >
          查询当前用户的默认预存签名。
          
      en: >
          Reads the current user's default preset signature.
          
deps:
  - kind: reference
    to: oa.sign.preset.profile
    from_api: "PUT /api/v1/sign/presets/{id}/default"
    to_api: "mysql:sys_user_signature"
    label: {zh: "默认标记落在预存签名记录上", en: "Default flag on preset record"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-002`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE sys_user_signature`（§2. 身份与组织）
