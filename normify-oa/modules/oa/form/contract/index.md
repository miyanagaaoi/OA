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
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.339Z"
fingerprint: 97e9e0f755e6415bda2b91292ed36f86aab2f88c855211177550c70dce54d7e8
source:
  - path: "doc/forms.md"
  - path: "doc/prd-0.1.md"
  - path: "oa-server/src/main/java/com/oa/form/contract/ContractFormRules.java"
  - path: "oa-server/src/main/java/com/oa/form/api/FormRuleController.java"
---

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
- `doc/prd-0.1.md` → `REQ-FORM-001`（§6.2 四类审批单与事项类别的关系）
