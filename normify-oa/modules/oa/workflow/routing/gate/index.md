---
uid: 33c6acdc
id: oa.workflow.routing.gate
parent: oa.workflow.routing
state: planned
name: {zh: "流转链两道闸门", en: "Routing Chain Gates"}
description:
  zh: >
      流转链的两道闸门与留痕：总次数闸门（流转+回退合计 ≤5，达上限拒绝继续并提示改用驳回或终止）与禁止回流闸门（已处理过的部门不可再次被指定为流转目标，「回到本部门」为唯一例外）；同时把流转/回退/补件动作统一写入审计。
      
  en: >
      The two gates of the routing chain plus its trail: the total-count gate (routing plus rollback at most five, after which further hops are refused with a hint to reject or terminate) and the no-reflux gate (a department already handled can never be designated again, back-home being the only exception); all routing, rollback and supplement actions are audited.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.308Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 335
    end_line: 338
  - path: "doc/prd-0.1.md"
    line: 361
    end_line: 361
---
