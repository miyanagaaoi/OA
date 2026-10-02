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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.681Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 25
    end_line: 35
---
