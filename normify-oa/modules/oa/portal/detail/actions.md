---
uid: 349bedb3
id: oa.portal.detail.actions
parent: oa.portal.detail
state: planned
name: {zh: "详情操作栏", en: "Detail Action Bar"}
description:
  zh: >
      浮层底部操作栏（高 60px，按钮右对齐）：同意 / 驳回 / 流转 / 回退上一节点 / 回到本部门 / 要求补充材料 / 终止 / 转办 / 打印；一屏一主按钮，危险操作二次确认且确认文案写明动作与对象；待补件期间其他角色只读不可审批。
      
  en: >
      The overlay footer action bar (60px tall, buttons right-aligned): approve, reject, route to another department, revert to previous node, return to own department, request more material, terminate, transfer and print; exactly one primary button, destructive actions need a second confirmation naming action and object, and while awaiting material the document is read-only for every other role.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.568Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 775
    end_line: 775
  - path: "DESIGN.md"
    line: 910
    end_line: 910
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/detail/{instance_id}/actions/{action}"
    description:
      zh: >
          提交一个审批动作（同意 / 驳回 / 流转 / 回退 / 终止 / 补件）。
          
      en: >
          Submit one approval action (approve, reject, route, revert, terminate, supplement).
          
  - protocol: http
    method: GET
    path: "/api/v1/portal/detail/{instance_id}/available-actions"
    description:
      zh: >
          当前用户可用动作（受节点状态与角色约束）。
          
      en: >
          Actions available to the current user, constrained by node state and role.
          
  - protocol: http
    method: GET
    path: "/detail/{instance_id}/print-preview"
    description:
      zh: >
          打印预览页（渲染该实例的 A4 打印稿）。
          
      en: >
          Print preview route rendering the A4 sheet for this instance.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/portal/detail/{instance_id}/actions/{action}"
    label: {zh: "提交审批动作", en: "Submit approval action"}
  - kind: call
    to: oa.workflow.exception
    from_api: "POST /api/v1/portal/detail/{instance_id}/actions/{action}"
    label: {zh: "回退与终止流程", en: "Flow revert & terminate"}
  - kind: call
    to: oa.workflow.supplement
    from_api: "POST /api/v1/portal/detail/{instance_id}/actions/{action}"
    label: {zh: "要求补充材料", en: "Request more material"}
  - kind: call
    to: oa.sign.capture
    from_api: "POST /api/v1/portal/detail/{instance_id}/actions/{action}"
    label: {zh: "采集审批签名", en: "Capture approval signature"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
