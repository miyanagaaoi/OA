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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.425Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-002`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_template`（§4. 流程定义）
