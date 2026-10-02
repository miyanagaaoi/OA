---
uid: "28508023"
id: oa.sign.preset.picker
parent: oa.sign.preset
state: planned
name: {zh: "审批时调用预存签名", en: "Preset Signature Picker"}
description:
  zh: >
      审批签名弹窗中列出当前用户可用预存签名（默认签名置顶），一键调用生成本次签名；仍按当前审批动作写入新的签名记录，不复用旧记录。
      
  en: >
      Lists the user's available preset signatures (default first) in the approval dialog; using one generates a fresh signature record rather than reusing an old one.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.742Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 365
    end_line: 366
apis:
  - protocol: http
    method: GET
    path: "/api/v1/sign/presets/picker"
    description:
      zh: >
          列出审批可用的预存签名（默认签名置顶）。
          
      en: >
          Lists preset signatures available for approval, default first.
          
  - protocol: http
    method: POST
    path: "/api/v1/sign/presets/{id}/use"
    description:
      zh: >
          一键调用预存签名生成本次签名。
          
      en: >
          Uses a preset signature to create the current signature.
          
deps:
  - kind: call
    to: oa.sign.record.append
    from_api: "POST /api/v1/sign/presets/{id}/use"
    to_api: "POST /api/v1/sign/records"
    label: {zh: "生成本次签名记录", en: "Create signature record"}
---
