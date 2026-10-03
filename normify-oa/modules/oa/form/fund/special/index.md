---
uid: 4669aeea
id: oa.form.fund.special
parent: oa.form.fund
name: {zh: "资金单专用字段与敏感项", en: "Fund-Specific & Sensitive Fields"}
description:
  zh: >
      资金单特有内容：计划类别与付款归属两个 checkbox 字段，一期只存不用（Q8/Q9，不得被流程条件、数据域过滤或超时规则引用）；收款账号的加密存储与角色脱敏。
      
  en: >
      Fund-only content: the plan category and payment belong checkboxes, stored but unused in phase one (Q8/Q9, never referenced by flow conditions, data-scope filters or timeout rules), plus payee-account encryption and role masking.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.012Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
---

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
