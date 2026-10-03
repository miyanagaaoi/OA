---
uid: 88f4eeb6
id: oa.notify.reminder.scan.node
parent: oa.notify.reminder.scan
name: {zh: "节点超时扫描", en: "Node Timeout Scan"}
description:
  zh: >
      扫描停留超时的审批任务，以节点配置的超时时长（≥24h，未配置不催办）为基准产出催办信号；超时仅催办，不改变任务与节点状态。
      
  en: >
      Scans stalled approval tasks against the configured node timeout (≥24h, no reminder when unset) and emits reminder signals without changing task or node state.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.496Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/notifications/reminders/overdue-tasks"
    description:
      zh: >
          扫描超时未处理的审批任务。
          
      en: >
          Scans for approval tasks that exceeded the node timeout.
          
  - protocol: kafka
    path: "oa.notify.reminder.due"
    description:
      zh: >
          超时催办信号事件。
          
      en: >
          Reminder signal emitted for an overdue task.
          
deps:
  - kind: reference
    to: oa.workflow.task
    label: {zh: "任务停留时间基准", en: "Task dwell-time basis"}
  - kind: reference
    to: oa.workflow.definition
    label: {zh: "节点超时时长配置", en: "Node timeout config"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
