---
uid: 1432c130
id: oa.form.template.write-model.state-whitelist
parent: oa.form.template.write-model
name: {zh: "状态白名单校验", en: "State Whitelist Guard"}
description:
  zh: >
      按单据状态计算可写字段集合并拦截越权写入：草稿全可写；审批中全只读（唯一例外＝印鉴单 return_status / return_date，仅发起人与节点⑦）；**待补件按字段类型放行所有附件类字段（type ∈ {file, files}）＋补件说明 supplement_note**，主字段一律只读（40304）；已完结只读。判据是字段类型，不是名为 attachments 的字段码。状态取自流程实例。
      
  en: >
      Computes the writable field set per document state and blocks out-of-scope writes: all fields in draft; read-only while approving (sole exception: the seal form's return_status / return_date for the initiator and node ⑦); while awaiting supplement every attachment-typed field (type ∈ {file, files}) plus supplement_note is writable while main fields stay read-only; closed documents are read-only. The test is the field type, not the field code named attachments.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.053Z"
fingerprint: 1ba1928c51160fbc60a41e566345596b56340383dc97322ce98630f93d7d123e
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/writemodel/FormStateWriteGuard.java"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/instances/{instance_id}/write-guard"
    description:
      zh: >
          写入前状态白名单校验。
          
      en: >
          Guards a write against the state whitelist.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/instances/{instance_id}/writable-fields"
    description:
      zh: >
          返回当前状态下的可写字段集合。
          
      en: >
          Returns the writable field set for the current state.
          
deps:
  - kind: dataflow
    to: oa.workflow.runtime
    from_api: "GET /api/v1/forms/instances/{instance_id}/writable-fields"
    label: {zh: "读取实例状态", en: "Read instance state"}
---

## 证据锚点
- `doc/forms.md` → `### 1.2 字段的三态读写模型（**核心约束**）`（§1.2 字段的三态读写模型）
