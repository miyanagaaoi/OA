---
uid: 1432c130
id: oa.form.template.write-model.state-whitelist
parent: oa.form.template.write-model
name: {zh: "状态白名单校验", en: "State Whitelist Guard"}
description:
  zh: >
      按单据状态计算可写字段集合并拦截越权写入：待补件下主字段一律只读，需改主字段必须走「驳回 → 修改 → 重新提交」；状态取自流程实例。
      
  en: >
      Computes the writable field set per document state and blocks out-of-scope writes: while awaiting supplement all main fields are read-only and changes must go through reject → edit → resubmit; state comes from the process instance.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.370Z"
fingerprint: 8cb8ee719ee014a8f11e4e86597e356444ff53a872cea106c704e314271bd8af
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
