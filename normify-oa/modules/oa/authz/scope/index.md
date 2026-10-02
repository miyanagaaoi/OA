---
uid: 1c2e4d34
id: oa.authz.scope
parent: oa.authz
state: planned
name: {zh: "数据域口径", en: "Data Scope Rules"}
description:
  zh: >
      可验收的数据域口径（V0.4）：普通员工仅本人相关单据、部门/科室负责人限本单位、子公司总经理限本公司、集团董事长与系统管理员见全集团；**集团职能部门（财务部）按「归口类别（资金/合同/印鉴）＋涉及费用的事项单＋流转链承接/经过的单据」可见，不涉及费用且未经流转的事项单不可见**（Q13）；以查询过滤实现强制隔离。
      
  en: >
      Verifiable data-scope rules (V0.4): employees see their own documents, department leaders their unit, subsidiary GM their company, chairman and admins everything; the group function department (Finance) sees its ownership categories (fund/contract/seal), cost-involving matter forms, and documents routed to or through it - matter forms without cost and without routing stay invisible. Enforced by query filtering.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.635Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 169
    end_line: 183
  - path: "doc/data-model.md"
    line: 668
    end_line: 738
---
