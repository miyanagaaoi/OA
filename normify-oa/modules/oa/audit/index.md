---
uid: 60798a28
id: oa.audit
parent: oa
name: {zh: "审计与留痕", en: "Audit & Traceability"}
description:
  zh: >
      操作日志、审批轨迹、登录日志与权限变更日志；全部只追加、禁止修改删除，审计日志与审批轨迹保留不少于 10 年、登录日志 1 年，并保证哈希链路的完整性。
      
  en: >
      Operation logs, approval traces, login logs and permission-change logs: append-only, non-deletable, retained ten years or more (login logs one year), with integrity hashing.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.934Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.9 审计日志`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
