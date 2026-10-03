---
uid: 37b1a2c1
id: oa.admin.flow.template
parent: oa.admin.flow
state: planned
name: {zh: "流程模板配置", en: "Flow Template Config"}
description:
  zh: >
      编排流程模板与节点，经版本发布上线，并配置审批人解析规则、决议模式与阈值、签名要求、超时时长与流转闸门。
      
  en: >
      Author flow templates and their nodes, publish them through versioned publishing, and configure approver resolution, decision modes, thresholds, signatures, timeouts and routing gates.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.126Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-002`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_template`（§4. 流程定义）
