---
uid: 22fe2d33
id: oa.admin.org.tree.leader
parent: oa.admin.org.tree
name: {zh: "负责人维护", en: "Org Leader Maintenance"}
description:
  zh: >
      维护组织正职/副职负责人（支持多负责人与一人多岗），可绑定事项类别作为审批人解析的唯一权威来源；负责人空缺会影响发起拦截校验。
      
  en: >
      Maintain primary and deputy org leaders (multiple leaders and multiple posts per person), optionally bound to a matter category as the single authoritative source for approver resolution; a vacancy affects the initiate-time block check.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.199Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/orgs/{org_id}/leaders"
    description:
      zh: >
          查询组织负责人（正职/副职）。
          
      en: >
          List org leaders (primary/deputy).
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/orgs/{org_id}/leaders"
    description:
      zh: >
          新增负责人，可绑定正副职与事项类别。
          
      en: >
          Add a leader with type and optional matter category.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/org-leaders/{leader_id}"
    description:
      zh: >
          移除负责人并提示受影响节点。
          
      en: >
          Remove a leader and report affected nodes.
          
deps:
  - kind: call
    to: oa.workflow.approver
    from_api: "POST /api/v1/admin/orgs/{org_id}/leaders"
    label: {zh: "负责人供审批人解析使用", en: "Feeds approver resolution"}
  - kind: dataflow
    to: oa.identity.position.leader-bind
    to_api: "mysql:sys_org_leader"
    label: {zh: "写入组织负责人", en: "Write org leader binding"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_org_leader`（§2. 身份与组织）
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
