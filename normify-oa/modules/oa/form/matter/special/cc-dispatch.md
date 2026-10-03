---
uid: 392fce96
id: oa.form.matter.special.cc-dispatch
parent: oa.form.matter.special
name: {zh: "抄送人写入与通知", en: "CC Write & Notify"}
description:
  zh: >
      抄送人（≤20 人、通讯录内、去重）在提交时写入并派发抄送通知；抄送人可见单据但不产生待办、不参与审议。
      
  en: >
      CC users (≤20, from the directory, de-duplicated) are persisted at submission and receive CC notices; they can view the document but get no todo and take no part in the decision.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.271Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/matter/fields/cc-users/dispatch"
    description:
      zh: >
          写入抄送人并派发抄送通知。
          
      en: >
          Persists CC users and dispatches CC notices.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/instances/{instance_id}/cc-users"
    description:
      zh: >
          读取单据抄送人清单。
          
      en: >
          Reads a document's CC list.
          
deps:
  - kind: call
    to: oa.notify.cc
    from_api: "POST /api/v1/forms/matter/fields/cc-users/dispatch"
    label: {zh: "抄送通知派发", en: "Dispatch CC notices"}
---

## 证据锚点
- `doc/forms.md` → `## 2. 事项审批单（`form_type = matter`）`（§2. 事项审批单）
