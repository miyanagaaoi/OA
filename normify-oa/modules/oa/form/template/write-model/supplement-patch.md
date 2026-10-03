---
uid: 1cdf6de6
id: oa.form.template.write-model.supplement-patch
parent: oa.form.template.write-model
state: planned
name: {zh: "补件写入", en: "Supplement Write"}
description:
  zh: >
      待补件状态下的写入通道：仅 `attachments` 与 `supplement_note` 可写；`supplement_note`（≥5 字符、≤500）写入 `flow_supplement.submitted_note` 并进入审批轨迹，附件按轮次入库。
      
  en: >
      The write channel while awaiting supplement: only `attachments` and `supplement_note` are writable; `supplement_note` (≥5 and ≤500 characters) lands in `flow_supplement.submitted_note` and enters the approval trail, with attachments stored per round.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.365Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/instances/{instance_id}/supplement"
    description:
      zh: >
          提交补件说明与补件附件。
          
      en: >
          Submits a supplement note with attachments.
          
deps:
  - kind: call
    to: oa.workflow.supplement
    from_api: "POST /api/v1/forms/instances/{instance_id}/supplement"
    label: {zh: "补件流程与次数限制", en: "Supplement flow limits"}
  - kind: dataflow
    to: oa.workflow.supplement.request
    from_api: "POST /api/v1/forms/instances/{instance_id}/supplement"
    to_api: "mysql:flow_supplement"
    label: {zh: "读取补件记录", en: "Read supplement records"}
---

## 证据锚点
- `doc/forms.md` → `## 8. 补件说明字段（跨表单共用）`（§8. 补件说明字段）
