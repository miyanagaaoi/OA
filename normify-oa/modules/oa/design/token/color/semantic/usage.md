---
uid: 83e95c73
id: oa.design.token.color.semantic.usage
parent: oa.design.token.color.semantic
state: planned
name: {zh: "状态色使用边界", en: "Status Color Boundaries"}
description:
  zh: >
      状态与颜色映射全局唯一、实现时不得扩展：草稿/已关闭=neutral、待我审批=warning、审批中=info、已通过=success、已驳回/终止=error、已转办/已加签=tag-info；不得为不同单据类型发明新配色；确需区分多类别时优先用文字 + neutral 灰底标签。
  en: >
      Status and colour mapping is globally unique and must not be extended: draft and closed are neutral, pending my approval is warning, in approval is info, approved is success, rejected or terminated is error, and transferred or countersigned is tag-info; no document type may invent its own palette, and when categories genuinely need separation use text plus a neutral grey tag.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "DESIGN.md"
    line: 1128
    end_line: 1140
  - path: "DESIGN.md"
    line: 668
    end_line: 668
  - path: "doc/prd-0.1.md"
    line: 660
    end_line: 660
apis:
  - protocol: http
    method: GET
    path: "/design/status-map"
    description:
      zh: >
          状态与颜色映射查阅页（附录B，全局唯一且不得扩展）。
      en: >
          Status-to-colour mapping reference page (appendix B, globally unique and not extensible).
  - protocol: file
    path: "styles/tokens/semantic-usage.md"
    description:
      zh: >
          五套状态色的使用边界说明，作为评审对照清单。
      en: >
          Written boundary rules for the five status colours, used as a review checklist.
deps:
  - kind: reference
    to: oa.design.component
    label: {zh: "徽标取色的唯一来源", en: "Badge colour source"}
---
