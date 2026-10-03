---
uid: 2f494622
id: oa.admin.org.bulk.import
parent: oa.admin.org.bulk
name: {zh: "五类模板契约与导入类型清单", en: "Template Contracts & Import Kinds"}
description:
  zh: >
      五类 CSV 模板的**契约与错误码字典**：五个模板文件（组织/人员/负责人/岗位/角色分配）的权威列定义、逐行错误码（E-*-001..020，含 E-*-020 数据域越权码）与修正建议；kinds 接口把同一份元数据下发给前端，保证五步流水线与模板不会各说各话。
      
  en: >
      The contract of the five CSV templates and their error dictionary: which files exist (org / user / org_leader / user_position / user_role), their exact columns and per-row error codes (E-*-001..020, incl. the E-*-020 data-scope codes) with fix suggestions; the kinds endpoint exposes the same metadata to the UI so the five-step pipeline cannot drift from the templates.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.267Z"
fingerprint: 4e4f9da0fc6ef91304cf003737b5802df520dd0939ec9845b1bdda2d914324dc
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/ImportKind.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/ImportCodes.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/api/BulkImportController.java"
    line: 240
    end_line: 254
apis:
  - protocol: file
    path: "oa-deploy/import/org.csv"
    description:
      zh: >
          组织架构导入模板（UTF-8 BOM CSV，六列）。
          
      en: >
          Org-structure template (org_path, org_name, org_type, parent_path, status, remark).
          
  - protocol: file
    path: "oa-deploy/import/user.csv"
    description:
      zh: >
          人员导入模板（九列，含 employee_no 与 phone）。
          
      en: >
          User template (nine columns, incl. employee_no and phone).
          
  - protocol: file
    path: "oa-deploy/import/org_leader.csv"
    description:
      zh: >
          组织负责人导入模板。
          
      en: >
          Org-leader template.
          
  - protocol: file
    path: "oa-deploy/import/user_position.csv"
    description:
      zh: >
          岗位任职导入模板。
          
      en: >
          Post-assignment template.
          
  - protocol: file
    path: "oa-deploy/import/user_role.csv"
    description:
      zh: >
          角色分配导入模板。
          
      en: >
          User-role assignment template.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/bulk-import/kinds"
    description:
      zh: >
          五类导入元数据（kind/label/file/columns/previewRoute/commitRoute）。
          
      en: >
          Lists the five import kinds with file, columns and preview/commit routes.
          
---
