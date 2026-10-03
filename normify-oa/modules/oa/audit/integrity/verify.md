---
uid: 244534d3
id: oa.audit.integrity.verify
parent: oa.audit.integrity
state: planned
name: {zh: "完整性校验", en: "Integrity Verification"}
description:
  zh: >
      对签名哈希与日志记录做可校验性核对，输出校验作业与结果，作为不可篡改约束的验收证据；发现被改写痕迹时告警。
      
  en: >
      Verifies signature hashes and log records, producing verification jobs and results as acceptance evidence for the immutability constraint, and alerts when tampering traces are found.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:49:57.378Z"
fingerprint: 1d71d83c11c75a93b7ff4af24882ab247a2d9cf90243263cdddb7b8ade83fa75
source:
  - path: "doc/data-model.md"
    line: 832
    end_line: 867
apis:
  - protocol: http
    method: POST
    path: "/api/v1/audit/integrity/verify"
    description:
      zh: >
          对日志与签名发起完整性校验。
          
      en: >
          Starts an integrity verification over logs and signatures.
          
  - protocol: http
    method: GET
    path: "/api/v1/audit/integrity/verify/{job_id}"
    description:
      zh: >
          查询完整性校验结果。
          
      en: >
          Returns the integrity verification result.
          
deps:
  - kind: call
    to: oa.sign.record
    from_api: "POST /api/v1/audit/integrity/verify"
    label: {zh: "校验签名记录哈希", en: "Verify signature hashes"}
  - kind: reference
    to: oa.admin.boundary
    from_api: "GET /api/v1/audit/integrity/verify/{job_id}"
    label: {zh: "违规改写尝试告警", en: "Alert on tampering attempts"}
---
