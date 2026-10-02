---
uid: a0f77211
id: oa.notify.reminder.escalate
parent: oa.notify.reminder
state: planned
name: {zh: "催办抄送上级", en: "Escalate Reminder to Superior"}
description:
  zh: >
      催办可抄送上级：按组织负责人与岗位链解析上级并加入抄送，只增加可见性与提醒强度，不改变审批链、决议模式与决议权。
      
  en: >
      Optionally escalates a reminder by resolving the superior through the leader/post chain and adding them as CC, increasing visibility only and never changing the approval chain or decision rights.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.726Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 394
    end_line: 394
  - path: "doc/prd-0.1.md"
    line: 213
    end_line: 213
apis:
  - protocol: http
    method: POST
    path: "/api/v1/notifications/reminders/{id}/escalate"
    description:
      zh: >
          将催办抄送上级。
          
      en: >
          Escalates a reminder by CC-ing the superior.
          
  - protocol: http
    method: GET
    path: "/api/v1/notifications/reminders/{id}/recipients"
    description:
      zh: >
          查询催办收件人与上级链。
          
      en: >
          Reads the reminder recipients and leader chain.
          
deps:
  - kind: call
    to: oa.notify.cc.dispatch
    from_api: "POST /api/v1/notifications/reminders/{id}/escalate"
    to_api: "POST /api/v1/notifications/cc/resolve"
    label: {zh: "上级加入抄送", en: "Add superior to CC"}
  - kind: reference
    to: oa.identity.position
    label: {zh: "上级与负责人链解析", en: "Leader chain resolution"}
---
