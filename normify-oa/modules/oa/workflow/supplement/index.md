---
uid: 2d3d5e4a
id: oa.workflow.supplement
parent: oa.workflow
name: {zh: "补充材料", en: "Supplement Requests"}
description:
  zh: >
      补充材料：实例进入「待补件」暂停，仅附件与补件说明可写、已审批主字段只读，补件提交后直接回到请求节点，不算驳回不写驳回记录；同节点≤1 次、全单≤3 次，时限默认 3 个工作日，超时仅催办发起人。
      
  en: >
      Supplement requests: the instance pauses in a pending-supplement sub-status, only attachments and a supplement note may be written, submission returns to the requesting node (not the start), supplements do not count as rejections, limits are once per node and three per document with a three-working-day deadline.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.550Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-023`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_supplement`（§5. 流程运行时）
