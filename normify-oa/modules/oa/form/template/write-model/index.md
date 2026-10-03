---
uid: 13f2d711
id: oa.form.template.write-model
parent: oa.form.template
name: {zh: "三态读写模型", en: "Three-State Write Model"}
description:
  zh: >
      单据三个生命周期阶段（草稿全可写、审批中全只读、待补件仅附件与补件说明）的字段读写权限。服务端必须按状态白名单校验可写字段，不能仅依赖前端置灰；金额与账号类字段另有角色级脱敏。
      
  en: >
      Field read/write rights across the three lifecycle stages (draft fully writable, in-approval fully read-only, awaiting supplement only attachments and the supplement note). The server enforces a state whitelist rather than trusting greyed-out UI; amount and account fields add role-based masking.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.227Z"
fingerprint: c0490fc486d253a199c8879032b0ce102ff34e62c10a3b194a81a74a88b7d72d
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/writemodel/FormStateWriteGuard.java"
  - path: "oa-server/src/main/java/com/oa/form/template/writemodel/WriteContext.java"
  - path: "oa-server/src/main/java/com/oa/form/app/FormWritePolicy.java"
---

## 证据锚点
- `doc/forms.md` → `### 1.2 字段的三态读写模型（**核心约束**）`（§1.2 字段的三态读写模型）
