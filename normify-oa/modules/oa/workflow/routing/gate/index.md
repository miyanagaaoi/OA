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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.778Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 335
    end_line: 338
  - path: "doc/prd-0.1.md"
    line: 361
    end_line: 361
---
