---
uid: 2f57a1e6
id: oa.form.matter.fields.schedule-cc
parent: oa.form.matter.fields
name: {zh: "期望日期与抄送字段组", en: "Schedule & CC Field Group"}
description:
  zh: >
      期望完成日期 expect_date（date、非必填、不早于今天）与抄送人 cc_users（user、≤20 人、限通讯录内、去重）；抄送人只知会不审批。
      
  en: >
      Expected completion date `expect_date` (optional date not earlier than today) and CC users `cc_users` (user, ≤20 people, from the directory, de-duplicated); CC users are informed but do not approve.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.017Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/field-groups/schedule-cc"
    description:
      zh: >
          期望日期与抄送人字段组定义。
          
      en: >
          Expected-date and CC field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/matter/fields/cc-users/normalize"
    description:
      zh: >
          校验并去重抄送人（≤20 人、限通讯录）。
          
      en: >
          Validates and de-duplicates CC users (≤20, directory only).
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/fields/cc-users/candidates"
    description:
      zh: >
          通讯录候选抄送人。
          
      en: >
          Directory candidates for CC.
          
deps:
  - kind: reference
    to: oa.identity.user
    from_api: "GET /api/v1/forms/matter/fields/cc-users/candidates"
    label: {zh: "通讯录候选来源", en: "Directory candidates"}
---

## 证据锚点
- `doc/forms.md` → `## 2. 事项审批单（`form_type = matter`）`（§2. 事项审批单）
