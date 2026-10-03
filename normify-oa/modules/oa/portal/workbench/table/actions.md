---
uid: 0b49e39f
id: oa.portal.workbench.table.actions
parent: oa.portal.workbench.table
name: {zh: "行内操作", en: "Row Actions"}
description:
  zh: >
      表格行的行内操作：最多 3 个 ghost 按钮（查看 / 同意 / 转办），超出收进「更多」下拉；选中行用 primary-subtle 底 + 左侧 2px primary 指示条；驳回等破坏性动作用白底红字，二次确认文案须写明动作与对象（如「确认驳回《XX合同审批单》」）。
      
  en: >
      Row-level actions: at most three ghost buttons (view / approve / transfer), the rest folded into a More dropdown; the selected row uses a primary-subtle fill with a 2px primary indicator bar on the left; destructive actions such as reject use red text on white and a confirmation that names both the action and the object.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.518Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 839
    end_line: 842
  - path: "DESIGN.md"
    line: 758
    end_line: 758
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
