---
uid: 2b683d99
id: oa.form.template.snapshot
parent: oa.form.template
state: planned
name: {zh: "提交快照与模板版本", en: "Submission Snapshot"}
description:
  zh: >
      单据提交时把 `form_schema_json` 版本号与 `fields_json` 一并固化到 `form_data`，模板后续变更不影响在途单据；服务端只接受字符串或定点数金额。
      
  en: >
      On submission the `form_schema_json` version and `fields_json` are frozen into `form_data`, so later template changes never affect in-flight documents; the server accepts amounts only as strings or fixed-point numbers.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.355Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/instances/{instance_id}/snapshot"
    description:
      zh: >
          固化模板版本与提交数据。
          
      en: >
          Freezes the template version with the submitted data.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/instances/{instance_id}/snapshot"
    description:
      zh: >
          读取单据快照（字段与模板版本）。
          
      en: >
          Reads a document snapshot (fields and template version).
          
  - protocol: mysql
    path: "form_data"
    description:
      zh: >
          单据提交数据与模板版本快照表。
          
      en: >
          Document submission data and template-version snapshot table.
          
---

## 证据锚点
- `doc/forms.md` → `## 11. 表单模板实现要求`（§11. 表单模板实现要求）
