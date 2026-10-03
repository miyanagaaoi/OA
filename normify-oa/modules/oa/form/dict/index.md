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
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.005Z"
fingerprint: 5460deb08aa4c17fff80d41f4e0818ad08a8bd2e95712868c104ae72ef32e570
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/dict/FormDictService.java"
  - path: "oa-server/src/main/java/com/oa/form/dict/DictType.java"
---

## 证据锚点
- `doc/forms.md` → `## 6. 数据字典与布尔字段取值（6.7 / 6.8 不是字典）`（§6. 数据字典与布尔字段取值）
