---
uid: a307ccae
id: oa.form.print.variant.seal-cert
parent: oa.form.print.variant
name: {zh: "印鉴证照使用审批单", en: "Seal & Certificate Sheet"}
description:
  zh: >
      实单无对应参考件，沿用集团单版式推导；实测高度 205mm，单页余量充足；须体现用印类型、证照名称与证件归还状态。
      
  en: >
      No paper counterpart exists, so the layout derives from the group sheets; measured height 205mm leaves ample single-page room; it must show seal type, certificate name and certificate return status.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.278Z"
fingerprint: 0a8dce606796993b3c58b78c640b6eb33fbab91c821f159672f1f01fa1f7240d
source:
  - path: "DESIGN.md"
    line: 973
    end_line: 973
  - path: "DESIGN.md"
    line: 1046
    end_line: 1046
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `### 10.1 打印版式映射（四类单据 × 集团/子公司层）`（§10.1 打印版式映射）
