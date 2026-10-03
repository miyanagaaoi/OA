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
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.261Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-002`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_template`（§4. 流程定义）
