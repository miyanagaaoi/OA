---
uid: 1cfc3e48
id: oa.portal.initiate.draft
parent: oa.portal.initiate
state: planned
name: {zh: "草稿箱与续编提示", en: "Draft Box & Resume Prompt"}
description:
  zh: >
      顶部「保存草稿」与「草稿箱（N）」入口；检测到未提交的上次编辑时，顶部出现信息提示条「有上次编辑记录 / 继续编辑 / 删除」（semantic-info-surface 底）；草稿保留模板版本号，重新提交时按最新模板重新解析。
      
  en: >
      Top-bar entries for Save draft and Draft box (N); when an unsubmitted earlier edit is detected, an info bar appears with resume and delete actions on a semantic-info-surface fill; drafts keep the template version number and are re-resolved against the latest template on resubmission.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.780Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "DESIGN.md"
    line: 866
    end_line: 866
  - path: "doc/prd-0.1.md"
    line: 389
    end_line: 389
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/initiate/drafts"
    description:
      zh: >
          我的草稿列表（含模板版本号）。
          
      en: >
          My draft list, including template version numbers.
          
  - protocol: http
    method: POST
    path: "/api/v1/portal/initiate/drafts/{draft_id}"
    description:
      zh: >
          保存草稿内容与当前编辑进度。
          
      en: >
          Save draft content and current editing progress.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/portal/initiate/drafts/{draft_id}"
    description:
      zh: >
          删除草稿。
          
      en: >
          Delete a draft.
          
deps:
  - kind: call
    to: oa.form.matter
    from_api: "POST /api/v1/portal/initiate/drafts/{draft_id}"
    label: {zh: "草稿按表单结构暂存", en: "Draft kept as form structure"}
  - kind: call
    to: oa.form.template
    from_api: "GET /api/v1/portal/initiate/drafts"
    label: {zh: "草稿按模板版本还原", en: "Restore draft by template ver."}
---
