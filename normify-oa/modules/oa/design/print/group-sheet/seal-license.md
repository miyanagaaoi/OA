---
uid: bccffcd9
id: oa.design.print.group-sheet.seal-license
parent: oa.design.print.group-sheet
state: planned
name: {zh: "印鉴证照使用审批单", en: "Seal & Licence Sheet"}
description:
  zh: >
      印鉴证照使用审批单沿用集团单版式推导（实单无对应件）：使用单位 / 用印类型 / 证照名称 / 使用期限 / 证件归还状态按集团单三栏表头与写值区排列，签名栏与收尾行与集团单一致；实测高度 205mm，由用印类型与证照名称字段驱动。
      
  en: >
      The seal-and-licence approval sheet derives from the group sheet layout because no paper original exists: using unit, seal type, licence name, period of use and return status follow the group sheet three-column header and value cells, with the same signature bands and closing row; it measures 205mm and is driven by the seal-type and licence-name fields.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.703Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 966
    end_line: 973
  - path: "DESIGN.md"
    line: 1046
    end_line: 1046
apis:
  - protocol: file
    path: "print/templates/seal-license-a4.html"
    description:
      zh: >
          由集团单版式推导的印鉴证照使用审批单模板。
          
      en: >
          Seal and licence approval sheet template derived from the group sheet.
          
deps:
  - kind: reference
    to: oa.form.seal
    label: {zh: "用印与证照字段", en: "Seal & licence fields"}
---
