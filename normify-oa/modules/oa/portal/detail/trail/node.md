---
uid: 28da3c50
id: oa.portal.detail.trail.node
parent: oa.portal.detail.trail
state: planned
name: {zh: "轨迹节点四态", en: "Trail Node States"}
description:
  zh: >
      轨迹节点圆点四态：已完成（白底 + primary-border 边 + 墨色文字 + 勾选图标）、进行中（企业蓝实心 + 白字）、未到达（白底灰字灰边）、超时（白底红边红字）；节点间连接线 2px，已完成段 primary，未完成段 hairline。
      
  en: >
      The four trail node states: done (white fill, primary-border outline, ink text with a tick icon), current (solid corporate-blue with white text), not reached (white fill, grey text and border) and overdue (white fill, red text and border); connectors are 2px, with completed segments in primary and unfinished segments in hairline.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.278Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 879
    end_line: 879
  - path: "DESIGN.md"
    line: 922
    end_line: 922
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/detail/{instance_id}/trail"
    description:
      zh: >
          按时间排序的轨迹条目（审批人、意见、签名、时间）。
          
      en: >
          Time-ordered trail entries with approver, opinion, signature and time.
          
deps:
  - kind: call
    to: oa.audit.trace
    from_api: "GET /api/v1/portal/detail/{instance_id}/trail"
    label: {zh: "读取节点轨迹", en: "Read node trail"}
  - kind: dataflow
    to: oa.workflow.runtime.node-instance.state
    from_api: "GET /api/v1/portal/detail/{instance_id}/trail"
    to_api: "mysql:flow_node_instance"
    label: {zh: "读取节点实例状态", en: "Read node instances"}
---
