---
uid: 2ee7db2d
id: oa.sign.record.append
parent: oa.sign.record
state: planned
name: {zh: "签名记录只追加写入", en: "Append-Only Signature Records"}
description:
  zh: >
      签名记录（flow_signature）只追加：一次审批动作一条记录，重新签署产生新记录并保留旧记录，不覆盖、不删除（REQ-LOG-003）；写入前完成哈希计算与取证信息补齐。
      
  en: >
      Signature records (flow_signature) are append-only: one record per approval act, re-signing adds a new record while keeping the old one, never overwriting or deleting (REQ-LOG-003).
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.724Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 368
    end_line: 368
  - path: "doc/prd-0.1.md"
    line: 375
    end_line: 375
  - path: "doc/data-model.md"
    line: 533
    end_line: 563
apis:
  - protocol: mysql
    path: "flow_signature"
    description:
      zh: >
          签名记录表（只追加，含 CA 预留字段）。
          
      en: >
          Signature record table (append-only, with reserved CA fields).
          
  - protocol: http
    method: POST
    path: "/api/v1/sign/records"
    description:
      zh: >
          追加一条签名记录。
          
      en: >
          Appends one signature record.
          
  - protocol: http
    method: POST
    path: "/api/v1/sign/records/{id}/resign"
    description:
      zh: >
          重新签署：生成新记录并保留历史版本。
          
      en: >
          Re-signs by creating a new record while keeping the historical version.
          
deps:
  - kind: call
    to: oa.sign.record.hash
    from_api: "POST /api/v1/sign/records"
    to_api: "POST /api/v1/sign/records/hash"
    label: {zh: "计算防篡改哈希", en: "Compute tamper-evident hash"}
  - kind: reference
    to: oa.workflow.runtime
    label: {zh: "绑定流程实例", en: "Bound to workflow instance"}
---
