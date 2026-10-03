---
uid: 2459cc2d
id: oa.portal.detail.trail
parent: oa.portal.detail
name: {zh: "审批轨迹", en: "Approval Trail"}
description:
  zh: >
      审批轨迹区：按时间序列出每个节点的审批人、意见、签名图、时间与决议模式下各人结论（对应 REQ-LOG-002）；节点用四态圆点 + 2px 连接线表达进度，并行协同折叠为一个分组；轨迹只读、不可编辑删除。
      
  en: >
      The approval trail area: nodes in time order with approver, opinion, signature image, time and each person's conclusion under a decision mode (REQ-LOG-002); progress is expressed by four-state dots joined by 2px connectors, parallel collaboration folds into one group; the trail is read-only and can never be edited or deleted.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.106Z"
fingerprint: 6f97dc580ed4d0d87fae8063d855678f9a2f3b3425b149dd9321be8c4b605b79
source:
  - path: "doc/prd-0.1.md"
  - path: "DESIGN.md"
    line: 879
    end_line: 880
deps:
  - kind: call
    to: oa.audit.trace
    label: {zh: "读取审批轨迹", en: "Read approval trail"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
