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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.761Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 373
    end_line: 373
  - path: "doc/data-model.md"
    line: 756
    end_line: 767
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
