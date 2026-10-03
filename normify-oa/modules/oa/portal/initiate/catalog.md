---
uid: 10b0170a
id: oa.portal.initiate.catalog
parent: oa.portal.initiate
name: {zh: "单据类型入口", en: "Document Type Catalog"}
description:
  zh: >
      发起页的四类单据入口（事项 / 资金 / 合同 / 印鉴证照）：图标块统一 primary-subtle 底 + primary 图标，类型区分靠图标形状而非颜色；无权限模板不渲染；卡片式门户只在发起页与 H5 例外出现，列表仍以表格为默认形态。
      
  en: >
      Entry point for the four document types (matter / fund / contract / seal-and-licence): icon blocks all use a primary-subtle fill with a primary glyph, and types differ by icon shape rather than colour; templates outside the caller's permission are not rendered; card-style layout is an exception only on this page and on H5, lists otherwise default to tables.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.513Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 869
    end_line: 869
  - path: "DESIGN.md"
    line: 907
    end_line: 907
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/initiate/catalog"
    description:
      zh: >
          可选单据模板目录（按权限与数据域过滤）。
          
      en: >
          Catalog of selectable document templates, filtered by permission and data scope.
          
  - protocol: http
    method: POST
    path: "/api/v1/portal/initiate/drafts"
    description:
      zh: >
          按模板新建草稿并生成表单骨架。
          
      en: >
          Create a draft from a template and build its form skeleton.
          
deps:
  - kind: call
    to: oa.form.template
    from_api: "GET /api/v1/portal/initiate/catalog"
    label: {zh: "读取表单模板与字段字典", en: "Read templates & dictionary"}
  - kind: call
    to: oa.authz.scope
    from_api: "GET /api/v1/portal/initiate/catalog"
    label: {zh: "隐藏无权限模板", en: "Hide out-of-scope templates"}
  - kind: call
    to: oa.workflow.definition
    from_api: "POST /api/v1/portal/initiate/drafts"
    label: {zh: "绑定流程模板版本", en: "Bind workflow template version"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.2 四类审批单与事项类别的关系`（§6.2 四类审批单与事项类别的关系）
