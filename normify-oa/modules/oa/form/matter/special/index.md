---
uid: 3330c9b9
id: oa.form.matter.special
parent: oa.form.matter
state: planned
name: {zh: "事项单专用规则与联动", en: "Matter-Specific Rules & Linkage"}
description:
  zh: >
      事项单特有的业务规则：是否涉及费用作为全单唯一分支判据（决定是否跳过财务部复核）、事项类别提交后不可改判（只能驳回给发起人）、抄送人写入并触发抄送通知。
      
  en: >
      Matter-only business rules: cost involvement as the document's single branch criterion (whether the finance review node is skipped), the category being frozen after submission (reclassification requires rejecting back to the initiator), and CC users being written and notified.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.518Z"
fingerprint: bbeb3d9d134bd5a3a4751c83c995321260d43af7e642a30dcf9069bba7d15ac9
source:
  - path: "doc/forms.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/forms.md` → `## 9. 实单字段的处置决定（Q8–Q10 已关闭）`（§9. 实单字段的处置决定）
- `doc/prd-0.1.md` → `REQ-FORM-001`（§6.2 四类审批单与事项类别的关系）
