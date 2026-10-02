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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.700Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
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
