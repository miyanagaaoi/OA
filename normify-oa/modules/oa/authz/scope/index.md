---
uid: 1c2e4d34
id: oa.authz.scope
parent: oa.authz
name: {zh: "数据域口径", en: "Data Scope Rules"}
description:
  zh: >
      可验收的数据域口径（V0.4）：普通员工仅本人相关单据、部门/科室负责人限本单位、子公司总经理限本公司、集团董事长与系统管理员见全集团；**集团职能部门（财务部）按「归口类别（资金/合同/印鉴）＋涉及费用的事项单＋流转链承接/经过的单据」可见，不涉及费用且未经流转的事项单不可见**（Q13）；以查询过滤实现强制隔离。
      
  en: >
      Verifiable data-scope rules (V0.4): employees see their own documents, department leaders their unit, subsidiary GM their company, chairman and admins everything; the group function department (Finance) sees its ownership categories (fund/contract/seal), cost-involving matter forms, and documents routed to or through it - matter forms without cost and without routing stay invisible. Enforced by query filtering.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.687Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 169
    end_line: 183
  - path: "doc/data-model.md"
    line: 668
    end_line: 738
---
