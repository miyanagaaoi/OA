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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.288Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
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
