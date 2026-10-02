---
uid: 351652c8
id: oa.sign.record.immutable
parent: oa.sign.record
state: planned
name: {zh: "不可变约束", en: "Immutability Enforcement"}
description:
  zh: >
      签名记录的不可变约束：数据库触发器拒绝 UPDATE/DELETE（仅放行二期验签结果回写）、应用层仓储禁止修改删除，并以自检接口证明无绕过通路，对应验收项 AC-20。
      
  en: >
      Immutability of signature records: database triggers reject UPDATE/DELETE (only the CA verify-result write-back passes), the repository layer forbids mutation, and a self-check proves there is no bypass (AC-20).
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.745Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/data-model.md"
    line: 739
    end_line: 773
  - path: "doc/prd-0.1.md"
    line: 595
    end_line: 595
deps:
  - kind: reference
    to: oa.sign.record.append
    to_api: "mysql:flow_signature"
    label: {zh: "约束签名记录写入路径", en: "Constrains record writes"}
---
