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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.673Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 169
    end_line: 183
  - path: "doc/data-model.md"
    line: 668
    end_line: 738
---
