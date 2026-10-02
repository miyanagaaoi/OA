---
uid: 1e988ebe
id: oa.workflow.designer.publish
parent: oa.workflow.designer
state: planned
name: {zh: "版本发布走管理后台", en: "Publish via Admin Console"}
description:
  zh: >
      设计成果的发布链路：校验通过后生成待发布版本、提交发布申请，由管理后台完成发布并记录变更前后值与操作人；已发起实例仍按旧版本执行（REQ-FLOW-006、REQ-ADMIN-002、REQ-LOG-004）。
      
  en: >
      Publish pipeline for design output: after validation passes, generate the pending version, submit a publish request and let the admin console complete publishing while recording before/after values and the operator; in-flight instances keep running on the old version (REQ-FLOW-006, REQ-ADMIN-002, REQ-LOG-004).
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.802Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 349
    end_line: 349
  - path: "doc/prd-0.1.md"
    line: 436
    end_line: 436
  - path: "doc/prd-0.1.md"
    line: 579
    end_line: 579
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-designs/{template_id}/publish-requests"
    description:
      zh: >
          提交发布申请（含校验回执摘要）。
          
      en: >
          Submits a publish request with the validation summary.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-designs/{template_id}/publish-requests/{request_id}"
    description:
      zh: >
          查询发布申请状态与结果。
          
      en: >
          Queries the publish request status and result.
          
  - protocol: kafka
    path: "oa.workflow.template.publish-requested"
    description:
      zh: >
          发布申请提交事件，供管理后台与审计消费。
          
      en: >
          Event emitted when a publish request is submitted, consumed by the admin console and audit.
          
deps:
  - kind: call
    to: oa.workflow.definition.template.version
    from_api: "POST /api/v1/flow-designs/{template_id}/publish-requests"
    to_api: "POST /api/v1/flow-templates/{template_id}/versions"
    label: {zh: "生成待发布版本", en: "Create pending version"}
  - kind: call
    to: oa.admin.flow
    from_api: "POST /api/v1/flow-designs/{template_id}/publish-requests"
    label: {zh: "管理后台完成发布", en: "Admin console publishes"}
  - kind: dataflow
    to: oa.audit.oplog
    from_api: "kafka:oa.workflow.template.publish-requested"
    label: {zh: "变更前后值入日志", en: "Before/after values to log"}
---
