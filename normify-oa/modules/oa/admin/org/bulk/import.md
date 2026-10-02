---
uid: 2f494622
id: oa.admin.org.bulk.import
parent: oa.admin.org.bulk
state: planned
name: {zh: "Excel 批量导入", en: "Excel Bulk Import"}
description:
  zh: >
      导入组织架构与人员 Excel 模板，按行校验（账号唯一、组织路径存在、必填完整），支持预检-确认-执行三段式并回传失败行明细。
      
  en: >
      Import the org and user Excel templates with row-level validation (unique account, existing org path, required fields), in a preview-confirm-execute flow that returns failed-row details.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.669Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 435
    end_line: 435
  - path: "doc/prd-0.1.md"
    line: 242
    end_line: 242
apis:
  - protocol: file
    path: "import/orgs.xlsx"
    description:
      zh: >
          组织架构导入模板文件。
          
      en: >
          Org structure import template file.
          
  - protocol: file
    path: "import/users.xlsx"
    description:
      zh: >
          人员批量导入模板文件。
          
      en: >
          User bulk import template file.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/bulk-import/orgs"
    description:
      zh: >
          组织架构批量导入（预检后确认执行）。
          
      en: >
          Bulk import org structures after preview confirmation.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/bulk-import/users"
    description:
      zh: >
          人员批量导入（含角色与任职列）。
          
      en: >
          Bulk import users including role and post columns.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/bulk-import/{job_id}/result"
    description:
      zh: >
          查询导入结果与失败行明细。
          
      en: >
          Fetch import result with failed-row details.
          
deps:
  - kind: call
    to: oa.admin.org.bulk.impact
    from_api: "POST /api/v1/admin/bulk-import/orgs"
    label: {zh: "导入前必需的影响面预检", en: "Impact preview before import"}
---
