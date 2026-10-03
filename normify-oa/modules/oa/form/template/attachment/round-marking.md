---
uid: 2aef166a
id: oa.form.template.attachment.round-marking
parent: oa.form.template.attachment
state: planned
name: {zh: "补件轮次标记", en: "Supplement Round Marking"}
description:
  zh: >
      补件附件带 `round` 标记：0 = 原始附件，1..3 = 第 N 次补件；同一节点 ≤1 次、全单 ≤3 次；打印附件清单按轮次标注。
      
  en: >
      Supplement attachments carry a `round` marker: 0 for originals, 1..3 for the Nth supplement; at most one supplement per node and three per document; the printed attachment list labels each round.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.302Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 59
    end_line: 59
  - path: "doc/forms.md"
    line: 286
    end_line: 286
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/attachments/{attachment_id}/round"
    description:
      zh: >
          设置附件的补件轮次。
          
      en: >
          Sets an attachment's supplement round.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/instances/{instance_id}/attachments"
    description:
      zh: >
          按轮次列出单据附件。
          
      en: >
          Lists a document's attachments grouped by round.
          
deps:
  - kind: reference
    to: oa.workflow.supplement
    from_api: "POST /api/v1/forms/attachments/{attachment_id}/round"
    label: {zh: "补件轮次上限", en: "Supplement round limit"}
---
