---
uid: 81de221f
id: oa.form.seal.fields.period
parent: oa.form.seal.fields
name: {zh: "使用期限字段组", en: "Usage Period Field Group"}
description:
  zh: >
      使用开始日期 usage_start（date、必填、不早于今天）与使用结束日期 usage_end（date、必填、≥ usage_start）。
      
  en: >
      Usage start `usage_start` (date, required, not earlier than today) and usage end `usage_end` (date, required, ≥ start).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.279Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/field-groups/period"
    description:
      zh: >
          使用期限字段组定义。
          
      en: >
          Usage period field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/fields/usage-period/validate"
    description:
      zh: >
          校验使用期限开始不早于今天且结束 ≥ 开始。
          
      en: >
          Validates that usage start is not earlier than today and end ≥ start.
          
---

## 证据锚点
- `doc/forms.md` → `## 5. 印鉴证照审批单（`form_type = seal`）`（§5. 印鉴证照审批单）
