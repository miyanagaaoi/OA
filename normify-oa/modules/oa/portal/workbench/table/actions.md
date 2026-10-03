---
uid: 0b49e39f
id: oa.portal.workbench.table.actions
parent: oa.portal.workbench.table
state: planned
name: {zh: "行内操作", en: "Row Actions"}
description:
  zh: >
      表格行的行内操作：最多 3 个 ghost 按钮（查看 / 同意 / 转办），超出收进「更多」下拉；选中行用 primary-subtle 底 + 左侧 2px primary 指示条；驳回等破坏性动作用白底红字，二次确认文案须写明动作与对象（如「确认驳回《XX合同审批单》」）。
      
  en: >
      Row-level actions: at most three ghost buttons (view / approve / transfer), the rest folded into a More dropdown; the selected row uses a primary-subtle fill with a 2px primary indicator bar on the left; destructive actions such as reject use red text on white and a confirmation that names both the action and the object.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.786Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "DESIGN.md"
    line: 839
    end_line: 842
  - path: "DESIGN.md"
    line: 758
    end_line: 758
  - path: "doc/prd-0.1.md"
    line: 663
    end_line: 663
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/workbench/tasks/{task_id}/approve"
    description:
      zh: >
          行内快速同意（提交审批意见）。
          
      en: >
          Inline quick approve with an opinion.
          
  - protocol: http
    method: POST
    path: "/api/v1/portal/workbench/tasks/{task_id}/reject"
    description:
      zh: >
          行内驳回，意见必填且不少于 5 字。
          
      en: >
          Inline reject; the opinion is mandatory and at least five characters.
          
  - protocol: http
    method: POST
    path: "/api/v1/portal/workbench/tasks/{task_id}/transfer"
    description:
      zh: >
          行内转办给同一数据域内可见该单据的人。
          
      en: >
          Inline transfer to a user who can see the document in the same data scope.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/portal/workbench/tasks/{task_id}/approve"
    label: {zh: "提交审批动作", en: "Submit approval action"}
  - kind: call
    to: oa.workflow.exception
    from_api: "POST /api/v1/portal/workbench/tasks/{task_id}/transfer"
    label: {zh: "转办与改派规则", en: "Transfer & reassign rules"}
---
