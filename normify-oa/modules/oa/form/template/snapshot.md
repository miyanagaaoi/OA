---
uid: 2b683d99
id: oa.form.template.snapshot
parent: oa.form.template
name: {zh: "提交快照与模板版本", en: "Submission Snapshot"}
description:
  zh: >
      单据提交时把 `form_schema_json` 版本号与 `fields_json` 一并固化到 `form_data`，模板后续变更不影响在途单据；服务端只接受字符串或定点数金额。
      
  en: >
      On submission the `form_schema_json` version and `fields_json` are frozen into `form_data`, so later template changes never affect in-flight documents; the server accepts amounts only as strings or fixed-point numbers.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.290Z"
fingerprint: 889fa038560cd486e32ea40501330a851dd61a392e1eed1aff82eb9630912b30
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/snapshot/FormSnapshotService.java"
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
