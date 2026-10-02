---
uid: a307ccae
id: oa.form.print.variant.seal-cert
parent: oa.form.print.variant
state: planned
name: {zh: "印鉴证照使用审批单", en: "Seal & Certificate Sheet"}
description:
  zh: >
      实单无对应参考件，沿用集团单版式推导；实测高度 205mm，单页余量充足；须体现用印类型、证照名称与证件归还状态。
      
  en: >
      No paper counterpart exists, so the layout derives from the group sheets; measured height 205mm leaves ample single-page room; it must show seal type, certificate name and certificate return status.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.713Z"
fingerprint: f4473a365d6ffe090018b7c40180e7df64e246602e38a0c608b6eb114b853dce
source:
  - path: "DESIGN.md"
    line: 973
    end_line: 973
  - path: "DESIGN.md"
    line: 1046
    end_line: 1046
  - path: "doc/forms.md"
    line: 383
    end_line: 385
apis:
  - protocol: file
    path: "templates/print/seal-cert.html"
    description:
      zh: >
          印鉴证照使用审批单打印模板。
          
      en: >
          Print template for the seal & certificate sheet.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/seal/{instance_id}"
    description:
      zh: >
          渲染印鉴证照单打印稿。
          
      en: >
          Renders a seal & certificate document.
          
---
