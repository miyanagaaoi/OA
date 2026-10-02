---
uid: 0ddc6d0a
id: oa.portal.workbench.empty
parent: oa.portal.workbench
state: planned
name: {zh: "空状态与加载骨架", en: "Empty & Loading States"}
description:
  zh: >
      待办列表的空状态与加载态：空状态为 64px 单色线性图标（ink-disabled）+ 一行说明 + 一个主按钮「发起审批」，垂直居中、上下留白 48px；加载骨架用 surface-1 灰块且不改变行高，避免布局抖动。
      
  en: >
      Empty and loading states of the pending list: the empty state is a 64px monochrome line icon in ink-disabled plus one line of copy and a single primary button (Initiate approval), vertically centred with 48px of vertical whitespace; the loading skeleton uses surface-1 blocks that keep the row height stable to avoid layout shift.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.786Z"
fingerprint: ac0ee794ab636d5631ecf55f3fe0063493d1e3cafae26bb19c1ca5c017904aea
source:
  - path: "DESIGN.md"
    line: 884
    end_line: 884
  - path: "DESIGN.md"
    line: 644
    end_line: 644
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/workbench/summary"
    description:
      zh: >
          四类列表条数汇总，供标签徽标与空状态判定共用。
          
      en: >
          Counts for the four lists, shared by tab badges and empty-state detection.
          
deps:
  - kind: call
    to: oa.notify.inbox
    from_api: "GET /api/v1/portal/workbench/summary"
    label: {zh: "无待办但有未读消息时的空状态提示", en: "Hint: unread but no tasks"}
---
