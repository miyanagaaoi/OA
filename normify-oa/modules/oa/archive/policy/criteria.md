---
uid: 258f2c3c
id: oa.archive.policy.criteria
parent: oa.archive.policy
name: {zh: "归档条件判定", en: "Archive Criteria"}
description:
  zh: >
      依据实例状态（已通过、已驳回、已撤回、已终止）与完成时间满 3 年判定归档对象，生成归档候选清单并给出纳入或排除原因。
      
  en: >
      Decides archive targets from instance status (approved, rejected, withdrawn, terminated) plus finished_at older than three years, producing a candidate list with include/exclude reasons.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.920Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
- `doc/prd-0.1.md` → `REQ-NFR-010`（§第9章 非功能需求）
