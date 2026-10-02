---
uid: 7e91d536
id: oa.workflow.exception.timeout
parent: oa.workflow.exception
state: planned
name: {zh: "超时仅催办", en: "Timeout Reminder Only"}
description:
  zh: >
      每个节点可配置超时时长（须显式配置且 ≥24h）；超时后向审批人发送站内信与邮件催办，并可配置抄送其上级；一期明确不做超时自动跳过或自动升级，避免误判。
      
  en: >
      Each node may declare a timeout, which must be explicit and at least 24 hours. Once overdue the approver receives in-app and email reminders and the supervisor may optionally be cc'ed; the first phase explicitly performs no automatic skip and no automatic escalation, to avoid misjudged approvals.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.805Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 394
    end_line: 394
  - path: "doc/prd-0.1.md"
    line: 351
    end_line: 351
  - path: "doc/prd-0.1.md"
    line: 550
    end_line: 550
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/node-instances/{node_instance_id}/timeout-config"
    description:
      zh: >
          配置节点超时时长（≥24h，未配置则不启用）。
          
      en: >
          Configure the node timeout, at least 24 hours, disabled when unset.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/node-instances/{node_instance_id}/timeout-remind"
    description:
      zh: >
          超时催办（站内信 + 邮件，可抄送上级；不跳过不升级）。
          
      en: >
          Send overdue reminders in-app and by email, optionally cc the supervisor.
          
  - protocol: kafka
    path: "oa.workflow.task.overdue"
    description:
      zh: >
          节点超时事件（触发催办通知）。
          
      en: >
          Event emitted when a node passes its configured timeout.
          
deps:
  - kind: call
    to: oa.notify.reminder
    from_api: "kafka:oa.workflow.task.overdue"
    label: {zh: "站内信催办", en: "In-app reminder"}
  - kind: call
    to: oa.notify.mail
    from_api: "kafka:oa.workflow.task.overdue"
    label: {zh: "邮件催办", en: "Email reminder"}
  - kind: call
    to: oa.notify.cc
    from_api: "POST /api/v1/flow/node-instances/{node_instance_id}/timeout-remind"
    label: {zh: "可配置抄送上级", en: "Optional cc to supervisor"}
---
