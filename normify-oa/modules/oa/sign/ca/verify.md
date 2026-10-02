---
uid: 4ebba183
id: oa.sign.ca.verify
parent: oa.sign.ca
state: planned
name: {zh: "验签结果状态机", en: "CA Verify Result"}
description:
  zh: >
      二期 CA 验签的交互与结果状态机：valid / invalid / expired / revoked，写回 verify_result 与 verified_at；一期接口存在但返回「未启用」，不改动数据模型与审计链路。
      
  en: >
      The verification result state machine for phase-two CA (valid / invalid / expired / revoked) writing verify_result and verified_at; in phase one the endpoint reports not-enabled without changing the data model.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.784Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 373
    end_line: 373
  - path: "doc/prd-0.1.md"
    line: 376
    end_line: 376
  - path: "doc/data-model.md"
    line: 552
    end_line: 553
apis:
  - protocol: http
    method: POST
    path: "/api/v1/sign/ca/verify"
    description:
      zh: >
          触发 CA 验签（一期返回未启用）。
          
      en: >
          Triggers CA verification; phase one reports not-enabled.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/records/{id}/verify-result"
    description:
      zh: >
          查询某条签名记录的验签结果。
          
      en: >
          Reads the verification result of one signature record.
          
  - protocol: kafka
    path: "oa.sign.ca.verify-completed"
    description:
      zh: >
          验签完成事件。
          
      en: >
          Event published when verification completes.
          
deps:
  - kind: call
    to: oa.sign.record.immutable.verify-writeback
    from_api: "POST /api/v1/sign/ca/verify"
    to_api: "PATCH /api/v1/sign/records/{id}/verify-result"
    label: {zh: "回写验签结果", en: "Write back verify result"}
---
