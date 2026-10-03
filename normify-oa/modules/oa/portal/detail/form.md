---
uid: 2240cd98
id: oa.portal.detail.form
parent: oa.portal.detail
state: planned
name: {zh: "单据表单只读呈现", en: "Read-only Document Form"}
description:
  zh: >
      浮层内的单据表单：字段全部只读（提交后不可改，修改须由审批人驳回后重提），只读值用 canvas-subtle 底块呈现；金额 tnum 右对齐、两位小数，≥100 万显示万元换算；抬头含单据标题 + 状态徽标 + 单号，右侧放打印 / 流转 / 更多次要操作。
      
  en: >
      The document form inside the overlay: every field is read-only after submission (changes require an approver to reject and the initiator to resubmit) and read-only values use a canvas-subtle block; amounts are tnum right-aligned with two decimals and a ten-thousand-yuan conversion above one million; the header carries title, status badge and document number, with print / route / more as secondary actions on the right.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.646Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "DESIGN.md"
    line: 871
    end_line: 871
  - path: "DESIGN.md"
    line: 759
    end_line: 759
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/detail/{instance_id}/form"
    description:
      zh: >
          读取已提交的表单值、模板版本与字段级权限。
          
      en: >
          Read the submitted form values, template version and field-level permissions.
          
  - protocol: file
    path: "print/preview-{form_type}-a4.html"
    description:
      zh: >
          按单据类型渲染的 A4 打印稿模板。
          
      en: >
          A4 print sheet template rendered per document type.
          
deps:
  - kind: dataflow
    to: oa.workflow.runtime.instance.state
    from_api: "GET /api/v1/portal/detail/{instance_id}/form"
    to_api: "mysql:flow_instance"
    label: {zh: "读取实例状态与快照", en: "Read instance state"}
  - kind: reference
    to: oa.design.component
    label: {zh: "只读字段的呈现规范", en: "Read-only field styling"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
