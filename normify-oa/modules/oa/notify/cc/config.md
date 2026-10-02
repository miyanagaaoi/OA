---
uid: 6eb70d07
id: oa.notify.cc.config
parent: oa.notify.cc
state: planned
name: {zh: "抄送人配置", en: "CC Configuration"}
description:
  zh: >
      抄送人来源配置：发起人自选 + 流程模板固定抄送；模板固定抄送在设计器中配置，发起时与自选人合并去重；抄送人只读可见且不产生待办。
  en: >
      CC sources: initiator selection plus template-fixed CC configured in the designer; both are merged and de-duplicated at submission. CC recipients are read-only and get no task.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 409
    end_line: 409
  - path: "doc/data-model.md"
    line: 593
    end_line: 605
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow/templates/{template_id}/cc-rules"
    description:
      zh: >
          读取模板固定抄送规则。
      en: >
          Reads the template-fixed CC rules.
  - protocol: http
    method: PUT
    path: "/api/v1/flow/templates/{template_id}/cc-rules"
    description:
      zh: >
          配置模板固定抄送人（设计器）。
      en: >
          Configures template-fixed CC recipients.
  - protocol: http
    method: POST
    path: "/api/v1/instances/{instance_id}/cc"
    description:
      zh: >
          提交发起人自选抄送人。
      en: >
          Submits initiator-selected CC recipients.
deps:
  - kind: reference
    to: oa.workflow.designer
    label: {zh: "模板固定抄送配置入口", en: "Fixed CC config entry"}
  - kind: reference
    to: oa.portal.initiate
    label: {zh: "发起时选择抄送人", en: "Pick CC at submission"}
---
