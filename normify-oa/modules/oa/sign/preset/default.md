---
uid: 1c6f6dfa
id: oa.sign.preset.default
parent: oa.sign.preset
state: planned
name: {zh: "默认签名切换", en: "Default Preset Signature"}
description:
  zh: >
      在用户多张预存签名中指定默认签名（sys_user_signature.is_default，同一用户仅一张默认为真）；审批签名弹窗默认选中该签名，一键调用。
      
  en: >
      Marks one preset signature as the default per user (is_default is exclusive) so the approval dialog preselects it for one-tap use.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.722Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 366
    end_line: 366
  - path: "doc/data-model.md"
    line: 131
    end_line: 141
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
