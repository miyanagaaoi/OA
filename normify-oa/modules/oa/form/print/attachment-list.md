---
uid: bb39bda5
id: oa.form.print.attachment-list
parent: oa.form.print
state: planned
name: {zh: "附件清单", en: "Attachment List"}
description:
  zh: >
      打印稿附「附件清单」区，只列文件名（不含文件本体），补件附件标注轮次；附送材料栏与附件清单联动。
      
  en: >
      The sheet carries an attachment list that names files only (never their bodies) and labels supplement rounds; the submitted-materials cell works together with that list.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.667Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 372
    end_line: 372
  - path: "doc/forms.md"
    line: 389
    end_line: 389
apis:
  - protocol: file
    path: "templates/print/partials/attachment-list.html"
    description:
      zh: >
          附件清单区片段。
          
      en: >
          Partial for the attachment list section.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/attachments"
    description:
      zh: >
          返回附件清单（含补件轮次）。
          
      en: >
          Returns the attachment list with supplement rounds.
          
deps:
  - kind: dataflow
    to: oa.form.template.attachment.round-marking
    from_api: "GET /api/v1/forms/print/{instance_id}/attachments"
    to_api: "GET /api/v1/forms/instances/{instance_id}/attachments"
    label: {zh: "附件与轮次来源", en: "Attachment and round source"}
---
