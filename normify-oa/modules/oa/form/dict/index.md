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
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.310Z"
fingerprint: 350a147c920a2bb4616252fa7969d626a2a793f4b99f9f83707013fc98c1a7d7
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/dict/FormDictService.java"
  - path: "oa-server/src/main/java/com/oa/form/dict/DictType.java"
---

## 证据锚点
- `doc/forms.md` → `## 6. 数据字典与布尔字段取值（6.7 / 6.8 不是字典）`（§6. 数据字典与布尔字段取值）
