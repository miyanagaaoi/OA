---
uid: 2aef166a
id: oa.form.template.attachment.round-marking
parent: oa.form.template.attachment
name: {zh: "补件轮次标记", en: "Supplement Round Marking"}
description:
  zh: >
      附件带 round 标记：0 = 原始附件（草稿/发起时上传），1..3 = 第 N 次补件。轮次由服务端判定，不由客户端传入：草稿恒为 0；待补件期取 flow_supplement 中处理中那条的 supplement_round（缺省退化为 supplement_count + 1 并夹到 1..3）。同一节点 ≤1 次、全单 ≤3 次；附件清单按轮次分组，打印附件清单同样标注轮次。
      
  en: >
      Attachments carry a round marker: 0 for originals uploaded at draft time, 1..3 for the Nth supplement. The round is decided by the server, never supplied by the client: 0 while in draft; during pending-supplement it comes from the supplement_round of the in-flight flow_supplement row (falling back to supplement_count + 1 clamped to 1..3). At most one supplement per node and three per document; the attachment list groups by round and the printed list labels it too.
      
revision: 2b35226a53c51a794d873f97783d9eb3d4382933
updated_at: "2026-10-03T08:28:42.526Z"
fingerprint: f9bfdc95d3c2ef8e06e722635a11fe20efe18556f22f6c6c38e17b95bda5c5c9
source:
  - path: "doc/forms.md"
  - path: "doc/enums.md"
  - path: "doc/data-model.md"
  - path: "oa-server/src/main/java/com/oa/form/attachment/app/AttachmentService.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/instances/{instance_id}/attachments"
    description:
      zh: >
          按轮次列出单据附件（响应 rounds 为 {轮次: [附件]} 的分组）。
          
      en: >
          Lists a document's attachments grouped by round (the rounds field maps round number to attachments).
          
deps:
  - kind: reference
    to: oa.workflow.supplement
    from_api: "GET /api/v1/forms/instances/{instance_id}/attachments"
    label: {zh: "读取处理中的补件请求以确定轮次", en: "Derives round from supplement"}
---

## 证据锚点
- `doc/forms.md` → `### 1.4 附件通用限制`（§1.4 附件通用限制）
- `doc/enums.md` → `## 12. 附件格式与轮次`（§12 附件格式与轮次，含 §12.3 轮次语义）
- `doc/data-model.md` → `CREATE TABLE flow_attachment`（§6.2 附件）
