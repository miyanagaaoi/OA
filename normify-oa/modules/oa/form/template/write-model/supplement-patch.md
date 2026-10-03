---
uid: 1cdf6de6
id: oa.form.template.write-model.supplement-patch
parent: oa.form.template.write-model
name: {zh: "补件写入", en: "Supplement Write"}
description:
  zh: >
      待补件状态下的写入通道：**所有附件类字段（schema 中 type ∈ {file, files}，如合同单的 counterparty_docs）＋补件说明 supplement_note 可写**——判据是字段类型，不是名为 attachments 的字段码；supplement_note（≥5 字符、≤500）写入 flow_supplement.submitted_note 并进入审批轨迹，附件按轮次入库。其它类型字段（文本/金额/日期/枚举）在待补件期仍然只读。
      
  en: >
      The write channel while awaiting supplement: every attachment-typed field (schema type ∈ {file, files}, e.g. the contract form's counterparty_docs) plus supplement_note are writable — the test is the field type, not the field code named attachments; supplement_note (≥5 and ≤500 characters) lands in flow_supplement.submitted_note and enters the approval trail, with attachments stored per round. Fields of any other type stay read-only while awaiting supplement.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.054Z"
fingerprint: 5abac5c20e8ad6a4ad1bb9d66df58fa025b9571cc0569f7b894d95b4b96a384d
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
