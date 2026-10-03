---
uid: 3e4c6f57
id: oa.form.fund
parent: oa.form
name: {zh: "资金审批单", en: "Fund Approval Form"}
description:
  zh: >
      资金审批单（form_type=fund）：金额必填（DECIMAL(18,2)，≤99,999,999,999.99）、附件必填；「计划类别」「付款归属」一期只存不用、不参与任何流程判断；≥100 万同时显示万元换算，非财务角色不可导出。
      
  en: >
      Fund approval form (form_type=fund): amount mandatory in DECIMAL(18,2) with two decimals and a 100-million ceiling, attachments mandatory, plus planned-category and payment-ownership fields that are stored but do not affect routing in phase one; amount shown with ten-thousand conversion and not exportable by non-finance roles.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.197Z"
fingerprint: 497bedda8bb237142b19a38b91a667970e06368152e9e36e721bebe6beeebf0b
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/fund/FundFormRules.java"
  - path: "oa-server/src/main/java/com/oa/form/api/FormRuleController.java"
---

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
