---
uid: cd4adb51
id: oa.design.print.field-map
parent: oa.design.print
state: planned
name: {zh: "打印字段标签映射", en: "Print Field Label Map"}
description:
  zh: >
      打印标签来自表单模板 form_schema_json 中每个字段的 printLabel（缺省沿用界面标签）与 printVisible（默认 true，内部备注类字段可关闭）；一期固定对照如 category→事项分类、amount→申请金额 / 合同金额、period_start+period_end→履约期限（合并单元格）、counterparty→合同签订主体（乙方）；附件清单区列文件名并标注补件轮次。
      
  en: >
      Print labels come from printLabel on each field of the template's form_schema_json (falling back to the interface label) and printVisible (true by default, switchable off for internal notes); the first release fixes mappings such as category to matter classification, amount to requested or contract amount, the two period fields merged into one contract-term cell and counterparty to party B; the attachment list names files and marks supplement rounds.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.617Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 360
    end_line: 394
apis:
  - protocol: file
    path: "print/field-label-map.json"
    description:
      zh: >
          界面标签→打印标签对照表与各单据类型的 printVisible 开关。
          
      en: >
          Interface-label to print-label map plus printVisible flags per document type.
          
deps:
  - kind: reference
    to: oa.form.template
    label: {zh: "打印标签与可见性", en: "printLabel & printVisible"}
---
