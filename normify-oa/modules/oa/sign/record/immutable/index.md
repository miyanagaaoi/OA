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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.760Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
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
