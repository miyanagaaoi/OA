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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.686Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
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
