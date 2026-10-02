---
uid: 9de3a1e8
id: oa.form.print.variant.subsidiary-internal
parent: oa.form.print.variant
state: planned
name: {zh: "子公司内部审批单", en: "Subsidiary Internal Sheet"}
description:
  zh: >
      与集团单结构不同：顶部信息条（左申请编号、右打印人/打印时间）、抬头、竖向字段表（标签列|值列 ×2：申请人/申请时间/所属部门/审批状态）、分组标题行（审批详情/合同有效期/我方信息/对方信息/审批记录）、审批记录流水（阶段·处理人/动作/时间·意见与附件）、撤回与重审各自独立一行。
      
  en: >
      Structurally different from the group sheets: top info bar (application number left, printer and time right), heading, vertical field table (label/value pairs for applicant, time, department, status), merged group titles (approval detail, contract validity, our info, counterparty info, approval records), an approval trail, and withdrawal and approval on separate lines.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.725Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 972
    end_line: 972
  - path: "DESIGN.md"
    line: 1015
    end_line: 1024
  - path: "DESIGN.md"
    line: 1045
    end_line: 1048
apis:
  - protocol: file
    path: "templates/print/subsidiary-internal.html"
    description:
      zh: >
          子公司内部审批单打印模板。
          
      en: >
          Print template for the subsidiary internal sheet.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/internal/{instance_id}"
    description:
      zh: >
          渲染子公司内部审批单打印稿。
          
      en: >
          Renders a subsidiary internal approval sheet.
          
---
