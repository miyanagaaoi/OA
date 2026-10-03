---
uid: b4c146d4
id: oa.form.print.field-render.visibility
parent: oa.form.print.field-render
name: {zh: "打印可见性", en: "Print Visibility"}
description:
  zh: >
      字段带 printVisible（默认 true）；内部备注类字段（如 cost_bearer）可设为 false；involve_cost 打印为「资金审批内容」正文的一部分，不单独成行。
      
  en: >
      Fields carry printVisible (default true); internal-note fields such as cost_bearer can be set false; involve_cost prints as part of the fund-approval body text rather than its own row.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.274Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/visible-fields"
    description:
      zh: >
          返回打印稿可见字段集合。
          
      en: >
          Returns the fields visible on the sheet.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/templates/{form_type}/schema/fields/{field_id}/print-visible"
    description:
      zh: >
          配置字段的 printVisible。
          
      en: >
          Configures a field's printVisible.
          
---

## 证据锚点
- `doc/forms.md` → `## 10. 打印稿（A4）字段要求`（§10. 打印稿）
