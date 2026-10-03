---
uid: 11d75f89
id: oa.workflow.task.decision.threshold
parent: oa.workflow.task.decision
state: planned
name: {zh: "通过阈值（百分比/绝对人数）", en: "Pass Threshold (percent / headcount)"}
description:
  zh: >
      会签通过阈值的解析与达标判定：支持「百分比」（如 66%）与「绝对人数」（如 2 人）两种写法，二者同时配置时以绝对人数优先；阈值随节点配置在发起时冻结进节点实例，判定按已同意人数与候选人总数实时计算，默认「过半」。
      
  en: >
      Parsing and evaluating countersign pass thresholds: a percentage (e.g. 66%) or an absolute headcount (e.g. 2 people), with the headcount winning when both are configured; the threshold is frozen into the node instance at submission and evaluated against approvals over total candidates, defaulting to a simple majority.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.459Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/thresholds/resolve"
    description:
      zh: >
          解析节点阈值配置为可判定形式（百分比或绝对人数，绝对优先）。
          
      en: >
          Resolve a threshold config into an evaluable form, preferring headcount.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/node-instances/{node_instance_id}/threshold-check"
    description:
      zh: >
          判定当前同意人数是否已达通过阈值。
          
      en: >
          Check whether approvals have reached the pass threshold.
          
deps:
  - kind: reference
    to: oa.workflow.task.decision.mode
    from_api: "GET /api/v1/flow/node-instances/{node_instance_id}/threshold-check"
    label: {zh: "阈值结论交给模式判定", en: "Pass threshold to mode check"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
