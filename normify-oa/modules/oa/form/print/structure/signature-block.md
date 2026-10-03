---
uid: aa2e0263
id: oa.form.print.structure.signature-block
parent: oa.form.print.structure
name: {zh: "签名栏", en: "Signature Blocks"}
description:
  zh: >
      强制签名节点在打印稿上留空白签名栏（`签名：____ 年 月 日`），资金单为集团职能部门/集团分管领导/集团董事长三段；已签署的展示签名缩略图 + 时间戳文字，印章/签名取自签名记录。
      
  en: >
      Nodes that require a signature leave a blank signature line on the sheet (signature ____ date); the fund sheet has three sections (group function department, group executives, chairman); already-signed blocks print a signature thumbnail with a timestamp, taking seals and signatures from signature records.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.276Z"
fingerprint: 0a8dce606796993b3c58b78c640b6eb33fbab91c821f159672f1f01fa1f7240d
source:
  - path: "DESIGN.md"
    line: 1000
    end_line: 1000
  - path: "DESIGN.md"
    line: 1008
    end_line: 1008
  - path: "doc/forms.md"
apis:
  - protocol: file
    path: "templates/print/partials/signature-block.html"
    description:
      zh: >
          多轮签名栏片段。
          
      en: >
          Partial for multi-section signature blocks.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/signatures"
    description:
      zh: >
          取签名与印章用于打印栏位。
          
      en: >
          Fetches signatures and seals for the print blocks.
          
deps:
  - kind: reference
    to: oa.sign.record
    from_api: "GET /api/v1/forms/print/{instance_id}/signatures"
    label: {zh: "签名与印章来源", en: "Signature source"}
---

## 证据锚点
- `doc/forms.md` → `## 10. 打印稿（A4）字段要求`（§10. 打印稿）
