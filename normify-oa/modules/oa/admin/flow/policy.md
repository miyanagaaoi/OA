---
uid: 470db49d
id: oa.admin.flow.policy
parent: oa.admin.flow
state: planned
name: {zh: "全局流程参数", en: "Global Flow Policies"}
description:
  zh: >
      维护运营期不经开发即可调整的参数集：流转+回退总次数上限 5、同一节点被回退上限 2、「回到本部门」连续上限 2、补件次数同节点 1 次/全单 3 次、补件时限 3 个工作日。
      
  en: >
      Owns the operational parameter set changed without a release: total routing plus rollback limit of 5, per-node rollback limit of 2, return-to-own-department limit of 2, supplement limits of 1 per node and 3 per document, and the 3-working-day supplement deadline.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.626Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 551
    end_line: 555
  - path: "doc/data-model.md"
    line: 399
    end_line: 401
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/flow-policies"
    description:
      zh: >
          查询全部可调流程参数与默认值。
          
      en: >
          Read every adjustable process parameter and its default.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/flow-policies/{policy_key}"
    description:
      zh: >
          调整参数（如流转+回退总次数上限）。
          
      en: >
          Adjust a parameter such as total routing limit.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/flow-policies/reset"
    description:
      zh: >
          恢复文档默认值。
          
      en: >
          Restore the documented default values.
          
deps:
  - kind: call
    to: oa.workflow.supplement
    from_api: "PUT /api/v1/admin/flow-policies/{policy_key}"
    label: {zh: "补件次数与时限", en: "Supplement limits"}
  - kind: call
    to: oa.workflow.exception
    from_api: "PUT /api/v1/admin/flow-policies/{policy_key}"
    label: {zh: "闸门上限供异常处理", en: "Gate limits for exception"}
---
