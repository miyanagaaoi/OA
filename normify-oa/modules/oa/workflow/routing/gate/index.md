---
uid: 33c6acdc
id: oa.workflow.routing.gate
parent: oa.workflow.routing
name: {zh: "流转链两道闸门", en: "Routing Chain Gates"}
description:
  zh: >
      流转链的两道闸门与留痕：总次数闸门（流转+回退合计 ≤5，达上限拒绝继续并提示改用驳回或终止）与禁止回流闸门（已处理过的部门不可再次被指定为流转目标，「回到本部门」为唯一例外）；同时把流转/回退/补件动作统一写入审计。
      
  en: >
      The two gates of the routing chain plus its trail: the total-count gate (routing plus rollback at most five, after which further hops are refused with a hint to reject or terminate) and the no-reflux gate (a department already handled can never be designated again, back-home being the only exception); all routing, rollback and supplement actions are audited.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.543Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-024`（§6.4 流程引擎核心能力）
