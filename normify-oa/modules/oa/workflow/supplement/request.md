---
uid: 3cab0588
id: oa.workflow.supplement.request
parent: oa.workflow.supplement
state: planned
name: {zh: "发起补件请求", en: "Raise a Supplement Request"}
description:
  zh: >
      审批节点发起补件请求：必须填写要求补充什么（reason），写 flow_supplement（node_instance_id 指向请求节点、supplement_round 递增、deadline 默认 3 个工作日），并把实例置入「待补件」子状态；同节点最多 1 次、全单最多 3 次。
      
  en: >
      An approval node raises a supplement request: the reason stating what is missing is mandatory, a flow_supplement row is written (node_instance_id points to the requesting node, supplement_round increments, deadline defaults to three working days) and the instance enters the pending-supplement sub-status. At most once per node and three times per document.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.318Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 325
    end_line: 325
  - path: "doc/prd-0.1.md"
    line: 360
    end_line: 360
  - path: "doc/data-model.md"
    line: 580
    end_line: 610
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/supplements"
    description:
      zh: >
          发起补件请求（原因必填，写轮次与时限）。
          
      en: >
          Raise a supplement request with a mandatory reason, round and deadline.
          
  - protocol: mysql
    path: "flow_supplement"
    description:
      zh: >
          补件请求记录（轮次、时限、状态、补件说明）。
          
      en: >
          Supplement request records: round, deadline, status and note.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/supplements"
    description:
      zh: >
          补件请求列表（第 N 次与状态）。
          
      en: >
          List supplement requests with their round and status.
          
deps:
  - kind: call
    to: oa.workflow.supplement.limit.quota
    from_api: "POST /api/v1/flow/instances/{instance_id}/supplements"
    to_api: "POST /api/v1/flow/instances/{instance_id}/supplement-quota/check"
    label: {zh: "补件次数上限校验", en: "Enforce supplement quota"}
  - kind: call
    to: oa.workflow.supplement.limit.deadline
    from_api: "POST /api/v1/flow/instances/{instance_id}/supplements"
    to_api: "POST /api/v1/flow/supplements/{supplement_id}/deadline"
    label: {zh: "计算三工作日时限", en: "Compute the deadline"}
  - kind: call
    to: oa.notify.inbox
    label: {zh: "站内信通知发起人", en: "Notify initiator in-app"}
  - kind: call
    to: oa.notify.mail
    label: {zh: "邮件通知发起人", en: "Email the initiator"}
---
