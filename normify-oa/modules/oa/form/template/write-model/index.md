---
uid: 13f2d711
id: oa.form.template.write-model
parent: oa.form.template
state: planned
name: {zh: "三态读写模型", en: "Three-State Write Model"}
description:
  zh: >
      单据三个生命周期阶段（草稿全可写、审批中全只读、待补件仅附件与补件说明）的字段读写权限。服务端必须按状态白名单校验可写字段，不能仅依赖前端置灰；金额与账号类字段另有角色级脱敏。
  en: >
      Field read/write rights across the three lifecycle stages (draft fully writable, in-approval fully read-only, awaiting supplement only attachments and the supplement note). The server enforces a state whitelist rather than trusting greyed-out UI; amount and account fields add role-based masking.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/forms.md"
    line: 25
    end_line: 35
---
