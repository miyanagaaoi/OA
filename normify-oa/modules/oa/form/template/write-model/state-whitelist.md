---
uid: 1432c130
id: oa.form.template.write-model.state-whitelist
parent: oa.form.template.write-model
state: planned
name: {zh: "状态白名单校验", en: "State Whitelist Guard"}
description:
  zh: >
      按单据状态计算可写字段集合并拦截越权写入：待补件下主字段一律只读，需改主字段必须走「驳回 → 修改 → 重新提交」；状态取自流程实例。
      
  en: >
      Computes the writable field set per document state and blocks out-of-scope writes: while awaiting supplement all main fields are read-only and changes must go through reject → edit → resubmit; state comes from the process instance.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.682Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 29
    end_line: 35
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
