---
uid: 258f2c3c
id: oa.archive.policy.criteria
parent: oa.archive.policy
state: planned
name: {zh: "归档条件判定", en: "Archive Criteria"}
description:
  zh: >
      依据实例状态（已通过、已驳回、已撤回、已终止）与完成时间满 3 年判定归档对象，生成归档候选清单并给出纳入或排除原因。
      
  en: >
      Decides archive targets from instance status (approved, rejected, withdrawn, terminated) plus finished_at older than three years, producing a candidate list with include/exclude reasons.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.677Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/data-model.md"
    line: 818
    end_line: 822
  - path: "doc/prd-0.1.md"
    line: 540
    end_line: 540
apis:
  - protocol: http
    method: GET
    path: "/api/v1/archive/candidates"
    description:
      zh: >
          列出满足归档条件的实例。
          
      en: >
          Lists instances eligible for archiving.
          
  - protocol: http
    method: POST
    path: "/api/v1/archive/candidates/evaluate"
    description:
      zh: >
          评估满 3 年的归档阈值。
          
      en: >
          Evaluates the three-year archive threshold.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "GET /api/v1/archive/candidates"
    label: {zh: "读取已完结实例状态与完成时间", en: "Read finished instance state"}
  - kind: reference
    to: oa.admin.boundary
    from_api: "POST /api/v1/archive/candidates/evaluate"
    label: {zh: "归档操作复核与留痕", en: "Review before archiving"}
---
