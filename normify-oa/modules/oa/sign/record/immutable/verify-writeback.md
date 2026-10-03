---
uid: "41250139"
id: oa.sign.record.immutable.verify-writeback
parent: oa.sign.record.immutable
state: planned
name: {zh: "验签结果回写通道", en: "Verify-Result Write-Back"}
description:
  zh: >
      二期 CA 验签结果的唯一允许更新路径：仅更新 verify_result 与 verified_at，其余字段不变，数据库触发器据此放行；该通道一期即存在但无调用方，越界回写视为篡改尝试。
      
  en: >
      The only permitted update path for CA verification results: it touches verify_result and verified_at only, which is exactly what the trigger allows; the path exists in phase one with no caller.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.592Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: PATCH
    path: "/api/v1/sign/records/{id}/verify-result"
    description:
      zh: >
          回写验签结果（唯一允许的更新路径）。
          
      en: >
          Writes back the verification result, the only permitted update.
          
  - protocol: kafka
    path: "oa.sign.record.verify-updated"
    description:
      zh: >
          验签结果更新事件。
          
      en: >
          Event published after a verification result update.
          
deps:
  - kind: reference
    to: oa.sign.ca.fields
    from_api: "PATCH /api/v1/sign/records/{id}/verify-result"
    to_api: "GET /api/v1/sign/ca/schema"
    label: {zh: "回写 CA 预留字段", en: "Writes back CA verify fields"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-004`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
