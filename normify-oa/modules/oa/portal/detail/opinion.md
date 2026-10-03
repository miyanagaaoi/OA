---
uid: "34936074"
id: oa.portal.detail.opinion
parent: oa.portal.detail
state: planned
name: {zh: "审批意见块", en: "Approval Opinion Block"}
description:
  zh: >
      审批意见块：canvas-subtle 底、rounded.sm、内边距 12px，结构为「意见正文 → 签名图（若有）→ 审批人 + 岗位 + 时间戳」；签名图与时间戳不可编辑、不可删除，仅可重新签署并保留历史版本（对应 REQ-SIGN-004）。
      
  en: >
      The approval opinion block: canvas-subtle fill, rounded.sm and 12px padding, structured as opinion text, then the signature image if any, then approver plus position and timestamp; the signature image and timestamp can never be edited or deleted, only re-signed with history kept (REQ-SIGN-004).
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.569Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 881
    end_line: 881
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/detail/{instance_id}/opinions"
    description:
      zh: >
          某实例的审批意见列表，含签名图与时间戳。
          
      en: >
          Approval opinions for an instance, each with signature image and timestamp.
          
deps:
  - kind: call
    to: oa.sign.record
    from_api: "GET /api/v1/portal/detail/{instance_id}/opinions"
    label: {zh: "读取意见与签名", en: "Opinion & signature lookup"}
  - kind: dataflow
    to: oa.sign.record.append
    from_api: "GET /api/v1/portal/detail/{instance_id}/opinions"
    to_api: "mysql:flow_signature"
    label: {zh: "签名记录只追加存储", en: "Signature record store"}
  - kind: reference
    to: oa.design.token
    label: {zh: "浅底块与圆角令牌", en: "Subtle block & radius tokens"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
