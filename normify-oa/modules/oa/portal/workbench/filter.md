---
uid: 0d2e386b
id: oa.portal.workbench.filter
parent: oa.portal.workbench
state: planned
name: {zh: "列表检索与筛选", en: "List Search & Filters"}
description:
  zh: >
      列表页内置检索器（系统刻意不做全局搜索框）：单据类型、状态、提交时间范围、发起人 / 部门、金额区间与单号关键字；筛选条件在四个标签页间保留；无数据权限的组织节点不进入筛选项（不可见优于不可用，防止探测组织架构）。
      
  en: >
      In-list search (the system deliberately has no global search box): document type, status, submission date range, initiator/department, amount range and document-number keyword; filters persist across the four tabs; organisation nodes outside the caller's data scope never appear as filter options.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.749Z"
fingerprint: 28e8829672cee9b922026028eb18feb80adbb9de4b7fc02f0910f372f46c48c2
source:
  - path: "DESIGN.md"
    line: 891
    end_line: 892
  - path: "doc/prd-0.1.md"
    line: 664
    end_line: 664
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/workbench/filters"
    description:
      zh: >
          返回可用筛选项（按数据域裁剪）。
          
      en: >
          Returns available filter options clipped to the caller's data scope.
          
  - protocol: http
    method: POST
    path: "/api/v1/portal/workbench/query"
    description:
      zh: >
          按筛选条件分页查询单据（每页 10/20/50）。
          
      en: >
          Paged document query by filter conditions (10/20/50 per page).
          
deps:
  - kind: call
    to: oa.authz.scope
    from_api: "GET /api/v1/portal/workbench/filters"
    label: {zh: "按数据域裁剪筛选项", en: "Clip filters by data scope"}
  - kind: call
    to: oa.form.dict
    from_api: "GET /api/v1/portal/workbench/filters"
    label: {zh: "读取单据类型与事项类别字典", en: "Read dict for type & category"}
  - kind: dataflow
    to: oa.form.template.snapshot
    from_api: "POST /api/v1/portal/workbench/query"
    to_api: "mysql:form_data"
    label: {zh: "按表单字段值筛选", en: "Filter by form values"}
---
