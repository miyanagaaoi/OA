---
uid: 4f5b7088
id: oa.sign.preset
parent: oa.sign
state: planned
name: {zh: "签名预存", en: "Signature Presets"}
description:
  zh: >
      用户在个人中心预存签名图片（上传或手写后保存），审批时一键调用；预存签名可修改，修改留痕并保留历史版本。
      
  en: >
      Users may store a signature image in their profile (upload or draw) for one-tap reuse during approval; editing a preset signature is logged and keeps history.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.666Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-002`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE sys_user_signature`（§2. 身份与组织）
