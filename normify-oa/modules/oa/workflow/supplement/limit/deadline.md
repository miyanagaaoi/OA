---
uid: 5b252f2f
id: oa.workflow.supplement.limit.deadline
parent: oa.workflow.supplement.limit
state: planned
name: {zh: "补件时限与超时催办", en: "Supplement Deadline & Overdue"}
description:
  zh: >
      补件时限默认 3 个工作日（deadline 在请求时按工作日历计算）；超时后 flow_supplement.status 置已超时，仅向发起人发送站内信与邮件催办，不自动驳回、不自动通过；扫描依据状态与时限索引。
      
  en: >
      The supplement deadline defaults to three working days and is computed on the working calendar when the request is made. Once overdue the record becomes overdue and only in-app and email reminders go to the initiator - never an automatic rejection or approval. The scan uses the status and deadline index.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:48:17.651Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 325
    end_line: 325
  - path: "doc/prd-0.1.md"
    line: 555
    end_line: 555
  - path: "doc/data-model.md"
    line: 510
    end_line: 510
  - path: "doc/prd-0.1.md"
    line: 523
    end_line: 523
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/supplements/{supplement_id}/deadline"
    description:
      zh: >
          计算并写入补件时限（请求后 3 个工作日）。
          
      en: >
          Compute and store the deadline three working days after the request.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/supplements/{supplement_id}/overdue-scan"
    description:
      zh: >
          超时扫描：状态置已超时并仅催办发起人。
          
      en: >
          Overdue scan: mark overdue and remind the initiator only.
          
  - protocol: kafka
    path: "oa.workflow.supplement.overdue"
    description:
      zh: >
          补件超时事件（触发催办通知）。
          
      en: >
          Event emitted when a supplement passes its deadline.
          
deps:
  - kind: call
    to: oa.notify.reminder
    from_api: "kafka:oa.workflow.supplement.overdue"
    label: {zh: "催办发起人", en: "Remind the initiator"}
  - kind: call
    to: oa.notify.mail
    from_api: "kafka:oa.workflow.supplement.overdue"
    label: {zh: "邮件催办发起人", en: "Email the initiator"}
---
