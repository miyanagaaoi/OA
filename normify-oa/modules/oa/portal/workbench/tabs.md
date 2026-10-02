---
uid: 054d276e
id: oa.portal.workbench.tabs
parent: oa.portal.workbench
state: planned
name: {zh: "待办标签页", en: "Workbench Tabs"}
description:
  zh: >
      审批中心的四个标签页——待我审批 / 我已审批 / 我发起的 / 抄送我的：高 40px、下划线指示器 2px 企业蓝，待办数量用徽标显示在标签右侧（不用彩色圆点）；标签栏固定不动，列表独立滚动。
      
  en: >
      The four workbench tabs — pending my approval, my approvals, raised by me, cc to me: 40px tall with a 2px corporate-blue underline indicator and a count badge beside each label (no coloured dots); the tab bar stays fixed while the list scrolls independently.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.782Z"
fingerprint: 28e8829672cee9b922026028eb18feb80adbb9de4b7fc02f0910f372f46c48c2
source:
  - path: "DESIGN.md"
    line: 883
    end_line: 883
  - path: "doc/prd-0.1.md"
    line: 667
    end_line: 669
apis:
  - protocol: http
    method: GET
    path: "/workbench/pending"
    description:
      zh: >
          待我审批标签页。
          
      en: >
          Pending-my-approval tab route.
          
  - protocol: http
    method: GET
    path: "/workbench/approved"
    description:
      zh: >
          我已审批标签页。
          
      en: >
          My approvals tab route.
          
  - protocol: http
    method: GET
    path: "/workbench/initiated"
    description:
      zh: >
          我发起的标签页。
          
      en: >
          Raised-by-me tab route.
          
  - protocol: http
    method: GET
    path: "/workbench/cc"
    description:
      zh: >
          抄送我的标签页。
          
      en: >
          Cc-to-me tab route.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "GET /workbench/pending"
    label: {zh: "拉取待办任务列表", en: "Fetch pending task list"}
  - kind: call
    to: oa.notify.inbox
    from_api: "GET /workbench/pending"
    label: {zh: "读取未读消息数用于标签徽标", en: "Unread count for tab badge"}
  - kind: dataflow
    to: oa.notify.cc.dispatch
    from_api: "GET /workbench/cc"
    to_api: "mysql:flow_cc"
    label: {zh: "抄送关系记录", en: "Cc relation records"}
---
