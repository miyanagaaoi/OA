---
uid: 1e988ebe
id: oa.workflow.designer.publish
parent: oa.workflow.designer
name: {zh: "版本发布走管理后台", en: "Publish via Admin Console"}
description:
  zh: >
      设计成果的发布链路：校验通过后生成待发布版本、提交发布申请，由管理后台完成发布并记录变更前后值与操作人；已发起实例仍按旧版本执行（REQ-FLOW-006、REQ-ADMIN-002、REQ-LOG-004）。
      
  en: >
      Publish pipeline for design output: after validation passes, generate the pending version, submit a publish request and let the admin console complete publishing while recording before/after values and the operator; in-flight instances keep running on the old version (REQ-FLOW-006, REQ-ADMIN-002, REQ-LOG-004).
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.537Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-002`（§6.10 管理后台）
