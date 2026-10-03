---
uid: 3e4c6f58
id: oa.form.contract
parent: oa.form
name: {zh: "合同审批单", en: "Contract Approval Form"}
description:
  zh: >
      合同审批单（form_type=contract）：对方主体、合同类型、金额与期限等要素，须上传合同文本附件；金额遵循只读与不可导出规则；集团层打印使用「集团合同类文件流转审批单」版式。
      
  en: >
      Contract approval form (form_type=contract): counterparty, contract type, amount and period fields plus a mandatory contract text attachment; subject also to the fund-style read-only and non-exportable amount rules; group-level output uses the group contract routing sheet.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.002Z"
fingerprint: 33f59e8835169e436acd28454a4beba0ebcd8991abeefb9f50ce434caef163cb
source:
  - path: "doc/forms.md"
  - path: "doc/prd-0.1.md"
  - path: "oa-server/src/main/java/com/oa/form/contract/ContractFormRules.java"
  - path: "oa-server/src/main/java/com/oa/form/api/FormRuleController.java"
---

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
- `doc/prd-0.1.md` → `REQ-FORM-001`（§6.2 四类审批单与事项类别的关系）
