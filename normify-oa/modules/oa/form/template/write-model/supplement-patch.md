---
uid: 1cdf6de6
id: oa.form.template.write-model.supplement-patch
parent: oa.form.template.write-model
name: {zh: "补件写入", en: "Supplement Write"}
description:
  zh: >
      待补件状态下的写入通道：仅 `attachments` 与 `supplement_note` 可写；`supplement_note`（≥5 字符、≤500）写入 `flow_supplement.submitted_note` 并进入审批轨迹，附件按轮次入库。
      
  en: >
      The write channel while awaiting supplement: only `attachments` and `supplement_note` are writable; `supplement_note` (≥5 and ≤500 characters) lands in `flow_supplement.submitted_note` and enters the approval trail, with attachments stored per round.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.371Z"
fingerprint: a5033f25b7d8f0482e18cc8dd361b23e4f3335e361d405a3652cb457322bafb6
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/app/FormWritePolicy.java"
  - path: "oa-server/src/main/java/com/oa/form/template/writemodel/FormStateWriteGuard.java"
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
