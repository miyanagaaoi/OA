---
uid: 3b5f3c8e
id: oa.sign.record.immutable.appguard
parent: oa.sign.record.immutable
state: planned
name: {zh: "应用层只追加纪律", en: "Append-Only App Discipline"}
description:
  zh: >
      应用层禁用签名记录与审计日志的 UPDATE/DELETE：仓储层不提供修改方法、ORM 不做脏写更新，并提供自检接口证明应用层不存在绕过数据库触发器的修改通路。
      
  en: >
      The application layer exposes no UPDATE/DELETE path for signature records or audit logs and offers a self-check endpoint proving there is no bypass around the database triggers.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.413Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/data-model.md"
    line: 741
    end_line: 745
  - path: "doc/prd-0.1.md"
    line: 431
    end_line: 431
apis:
  - protocol: http
    method: POST
    path: "/api/v1/sign/records/immutability/selfcheck"
    description:
      zh: >
          自检：确认无修改/删除通路。
          
      en: >
          Self-check that no update/delete path exists.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/records/immutability/report"
    description:
      zh: >
          查看最近一次不可变性自检报告。
          
      en: >
          Reads the latest immutability self-check report.
          
deps:
  - kind: reference
    to: oa.sign.record.append
    to_api: "POST /api/v1/sign/records"
    label: {zh: "被保护的写入模块", en: "Protected write module"}
---
