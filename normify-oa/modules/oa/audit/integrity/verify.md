---
uid: 244534d3
id: oa.audit.integrity.verify
parent: oa.audit.integrity
name: {zh: "完整性校验", en: "Integrity Verification"}
description:
  zh: >
      对签名哈希与日志记录做可校验性核对，输出校验作业与结果，作为不可篡改约束的验收证据；发现被改写痕迹时告警。
      
  en: >
      Verifies signature hashes and log records, producing verification jobs and results as acceptance evidence for the immutability constraint, and alerts when tampering traces are found.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.402Z"
fingerprint: d27f073aa0b7d919258285e22376dd379c55e1e74d7e28c48440b1100569087a
source:
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
