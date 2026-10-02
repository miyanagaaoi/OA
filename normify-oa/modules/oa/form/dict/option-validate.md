---
uid: 63f63a17
id: oa.form.dict.option-validate
parent: oa.form.dict
state: planned
name: {zh: "字典取值校验", en: "Dictionary Value Validation"}
description:
  zh: >
      提交时按绑定字典校验取值：code 必须存在于启用项，禁用项与未知 code 拒绝，多选去重；已提交单据按快照保存的中文名展示，不受字典后续增删影响。
      
  en: >
      Validates values against the bound dictionary at submission: the code must exist among enabled items, disabled or unknown codes are rejected, and multi-select values are de-duplicated; submitted documents display the snapshotted names regardless of later dictionary edits.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.659Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 176
    end_line: 178
  - path: "doc/forms.md"
    line: 400
    end_line: 400
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/dicts/{dict_key}/resolve"
    description:
      zh: >
          按 code 解析字典项名称。
          
      en: >
          Resolves dictionary item names by code.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/dicts/{dict_key}/validate-options"
    description:
      zh: >
          校验取值合法性与多选去重。
          
      en: >
          Validates option legality and de-duplicates multi-selects.
          
deps:
  - kind: reference
    to: oa.form.template.schema.dict-binding
    from_api: "POST /api/v1/forms/dicts/{dict_key}/validate-options"
    to_api: "GET /api/v1/forms/templates/{form_type}/schema/fields/{field_id}/options"
    label: {zh: "与字段绑定一致", en: "Matches field binding"}
---
