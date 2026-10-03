---
uid: 3e4c6f5a
id: oa.form.dict
parent: oa.form
name: {zh: "数据字典与下拉项", en: "Dictionary Options"}
description:
  zh: >
      四类单据共用的下拉字典：事项类别、合同类型、用印类型、证照类型、付款归属、计划类别等；由管理后台维护、可增删，运营期调整不经开发；历史单据保留当时所选值。
      
  en: >
      Dictionary options used by all forms - matter category, contract type, seal type, certificate type, payment ownership and planned category - maintained by admins at runtime without code changes; category values are configurable and historical documents keep the chosen value.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.261Z"
fingerprint: 90607856d87728d4f78aca5048f8a486389f9004f87617d538f296824d19d2fa
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/dict/FormDictService.java"
  - path: "oa-server/src/main/java/com/oa/form/dict/DictType.java"
---

## 证据锚点
- `doc/forms.md` → `## 6. 数据字典与布尔字段取值（6.7 / 6.8 不是字典）`（§6. 数据字典与布尔字段取值）
