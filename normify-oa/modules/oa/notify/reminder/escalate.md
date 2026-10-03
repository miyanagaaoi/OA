---
uid: a0f77211
id: oa.notify.reminder.escalate
parent: oa.notify.reminder
name: {zh: "催办抄送上级", en: "Escalate Reminder to Superior"}
description:
  zh: >
      催办可抄送上级：按组织负责人与岗位链解析上级并加入抄送，只增加可见性与提醒强度，不改变审批链、决议模式与决议权。
      
  en: >
      Optionally escalates a reminder by resolving the superior through the leader/post chain and adding them as CC, increasing visibility only and never changing the approval chain or decision rights.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.091Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
