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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.322Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 226
    end_line: 229
  - path: "doc/prd-0.1.md"
    line: 569
    end_line: 569
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
