---
uid: 3e4c6f57
id: oa.form.fund
parent: oa.form
state: planned
name: {zh: "资金审批单", en: "Fund Approval Form"}
description:
  zh: >
      资金审批单（form_type=fund）：金额必填（DECIMAL(18,2)，≤99,999,999,999.99）、附件必填；「计划类别」「付款归属」一期只存不用、不参与任何流程判断；≥100 万同时显示万元换算，非财务角色不可导出。
      
  en: >
      Fund approval form (form_type=fund): amount mandatory in DECIMAL(18,2) with two decimals and a 100-million ceiling, attachments mandatory, plus planned-category and payment-ownership fields that are stored but do not affect routing in phase one; amount shown with ten-thousand conversion and not exportable by non-finance roles.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.349Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
---

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
