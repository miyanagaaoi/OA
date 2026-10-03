---
uid: b49bcf34
id: oa.form.print.field-render.label-map
parent: oa.form.print.field-render
name: {zh: "打印标签映射", en: "Print Label Mapping"}
description:
  zh: >
      打印稿使用与纸质单一致的中文标签，每个字段带 printLabel，缺省沿用界面标签。一期固定对照：category→事项分类、amount→申请金额/合同金额、return_status→证件归还状态、period_start/period_end→履约期限（合并一个单元格）、counterparty→合同签订主体（乙方）、our_company→甲方/审批单位/用印单位（由发起人所属公司带出，不新增数据库列）、attachments→附送材料 + 附件清单。
      
  en: >
      Sheets use the paper form's Chinese labels; each field carries a printLabel defaulting to the screen label. Phase-one mapping: category→事项分类, amount→申请金额/合同金额, return_status→证件归还状态, period_start/period_end→履约期限 (one merged cell), counterparty→合同签订主体（乙方）, our_company→甲方/审批单位/用印单位 (derived from the initiator's company with no new column), attachments→附送材料 plus the attachment list.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.322Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/labels"
    description:
      zh: >
          返回单据字段的打印标签。
          
      en: >
          Returns print labels for a document's fields.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/templates/{form_type}/schema/fields/{field_id}/print-label"
    description:
      zh: >
          配置字段的 printLabel。
          
      en: >
          Configures a field's printLabel.
          
deps:
  - kind: reference
    to: oa.form.template.schema.field-def
    from_api: "GET /api/v1/forms/print/{instance_id}/labels"
    to_api: "GET /api/v1/forms/templates/{form_type}/schema"
    label: {zh: "字段定义来源", en: "Field definition source"}
---

## 证据锚点
- `doc/forms.md` → `## 10. 打印稿（A4）字段要求`（§10. 打印稿）
