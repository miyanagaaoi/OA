---
uid: 392fce96
id: oa.form.matter.special.cc-dispatch
parent: oa.form.matter.special
state: planned
name: {zh: "抄送人写入与通知", en: "CC Write & Notify"}
description:
  zh: >
      抄送人（≤20 人、通讯录内、去重）在提交时写入并派发抄送通知；抄送人可见单据但不产生待办、不参与审议。
      
  en: >
      CC users (≤20, from the directory, de-duplicated) are persisted at submission and receive CC notices; they can view the document but get no todo and take no part in the decision.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.719Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 86
    end_line: 86
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
