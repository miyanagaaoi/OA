---
uid: 52ffd370
id: oa.authz.scope.catalog
parent: oa.authz.scope
name: {zh: "数据域口径", en: "Data Scope Catalog"}
description:
  zh: >
      五种数据域取值（self 本人 / dept 本部门 / company 本公司 / group_all 全集团 / group_category 全集团按归口类别）及其对应角色的可见范围与判定依据；**V0.4：财务部 = group_category（资金/合同/印鉴 + 涉及费用事项单）∪ 流转链可见；group_all 仅保留给集团董事长与系统管理员**。
      
  en: >
      The five data-scope values (self / dept / company / group_all / group_category) with the roles they apply to and how visibility is decided. V0.4: Finance uses group_category (fund/contract/seal plus cost-involving matter forms) union routing-chain visibility; group_all is reserved for the chairman and system administrators.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.239Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/data-scopes"
    description:
      zh: >
          读取五种数据域取值与说明。
          
      en: >
          Lists the five data-scope values.
          
  - protocol: http
    method: PUT
    path: "/api/v1/authz/roles/{id}/data-scope"
    description:
      zh: >
          设置角色的数据域口径。
          
      en: >
          Sets the data scope of a role.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/scope-matrix"
    description:
      zh: >
          读取角色×可见范围口径表。
          
      en: >
          Reads the role-to-scope matrix.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-001`（§5.2 权限模型）
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
