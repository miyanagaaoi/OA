---
uid: 63f63a17
id: oa.form.dict.option-validate
parent: oa.form.dict
name: {zh: "字典取值校验", en: "Dictionary Value Validation"}
description:
  zh: >
      提交时按绑定字典校验取值：code 必须存在于启用项，禁用项与未知 code 拒绝，多选去重；已提交单据按快照保存的中文名展示，不受字典后续增删影响。
      
  en: >
      Validates values against the bound dictionary at submission: the code must exist among enabled items, disabled or unknown codes are rejected, and multi-select values are de-duplicated; submitted documents display the snapshotted names regardless of later dictionary edits.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.262Z"
fingerprint: 90607856d87728d4f78aca5048f8a486389f9004f87617d538f296824d19d2fa
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/dict/FormDictService.java"
  - path: "oa-server/src/main/java/com/oa/form/dict/DictType.java"
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

## 证据锚点
- `doc/forms.md` → `### 1.3 通用校验规则`（§1.3 通用校验规则）
