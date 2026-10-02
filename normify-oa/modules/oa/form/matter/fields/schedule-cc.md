---
uid: 2f57a1e6
id: oa.form.matter.fields.schedule-cc
parent: oa.form.matter.fields
state: planned
name: {zh: "期望日期与抄送字段组", en: "Schedule & CC Field Group"}
description:
  zh: >
      期望完成日期 expect_date（date、非必填、不早于今天）与抄送人 cc_users（user、≤20 人、限通讯录内、去重）；抄送人只知会不审批。
      
  en: >
      Expected completion date `expect_date` (optional date not earlier than today) and CC users `cc_users` (user, ≤20 people, from the directory, de-duplicated); CC users are informed but do not approve.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.664Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 85
    end_line: 86
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
