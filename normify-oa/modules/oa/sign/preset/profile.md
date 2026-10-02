---
uid: 15c873c1
id: oa.sign.preset.profile
parent: oa.sign.preset
state: planned
name: {zh: "预存签名管理", en: "Preset Signature Management"}
description:
  zh: >
      个人中心预存签名（sys_user_signature）：上传签名图片或手写后保存，支持列表、替换与删除（仅本人操作）；图片存私有化本地存储，库内只存路径或 base64。
      
  en: >
      Preset signatures in the personal center (sys_user_signature): upload an image or draw and save, list, replace and delete (owner only); images live in private local storage with only a path or base64 in the database.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.742Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 366
    end_line: 366
  - path: "doc/data-model.md"
    line: 128
    end_line: 141
apis:
  - protocol: mysql
    path: "sys_user_signature"
    description:
      zh: >
          用户预存签名表（sign_image / is_default，一人多张）。
          
      en: >
          User preset signature table (sign_image / is_default, several per user).
          
  - protocol: http
    method: POST
    path: "/api/v1/sign/presets"
    description:
      zh: >
          上传或手写保存一张预存签名。
          
      en: >
          Uploads or draws and saves a preset signature.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/presets"
    description:
      zh: >
          查询我的预存签名列表。
          
      en: >
          Lists my preset signatures.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/sign/presets/{id}"
    description:
      zh: >
          删除预存签名（仅本人）。
          
      en: >
          Deletes a preset signature (owner only).
          
deps:
  - kind: call
    to: oa.sign.preset.audit
    from_api: "POST /api/v1/sign/presets"
    to_api: "kafka:oa.sign.preset.changed"
    label: {zh: "预存签名修改留痕", en: "Trace preset signature change"}
  - kind: reference
    to: oa.identity.user
    label: {zh: "签名归属用户", en: "Owning user of the signature"}
---
