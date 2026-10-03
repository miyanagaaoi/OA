---
uid: "22725311"
id: oa.form.template.attachment.upload-policy
parent: oa.form.template.attachment
name: {zh: "上传策略与格式白名单", en: "Upload Policy"}
description:
  zh: >
      三档限额 + 单次上限：单文件 ≤ 50 MB、单次上传 ≤ 20 个、单字段 ≤ filePolicy.maxCount（缺省 20）、单张单据附件总数 ≤ 50 个（含补件）。格式**三重判断**：禁止格式 9 种（黑名单优先）→ 允许格式 15 种（含 wps / heic）→ 客户端声明 MIME 危险清单 → 魔数嗅探须与扩展名一致；文件名剥离路径与 ..，展示名与随机存储名分离。
      
  en: >
      Three limits plus a per-request cap: ≤50MB per file, ≤20 files per upload, ≤filePolicy.maxCount per field (20 by default), ≤50 attachments per document including supplements. Format is judged three ways: 9 forbidden formats first (blacklist wins), then the 15 allowed formats (wps/heic included), then a dangerous declared-MIME list, then magic-number sniffing that must match the extension; file names are stripped of paths and .., with display name kept separate from the random storage name.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.287Z"
fingerprint: 78edbefa0efd187778985fab00624fd0718633b7852fdb0cfa12e4ff96de4a13
source:
  - path: "doc/forms.md"
  - path: "doc/enums.md"
  - path: "doc/templates.md"
  - path: "oa-server/src/main/java/com/oa/form/attachment/domain/AttachmentPolicy.java"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormPayloadValidator.java"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/instances/{instance_id}/attachments"
    description:
      zh: >
          上传附件（multipart，字段名 files，可带 fieldCode）：三档限额 + 格式三重判断 + 内容嗅探，失败不落盘不落库。
          
      en: >
          Uploads attachments (multipart, part name files, optional fieldCode): three limits, three-way format check and content sniffing, with no file or row left behind on failure.
          
deps:
  - kind: call
    to: oa.form.template.write-model.state-whitelist
    from_api: "POST /api/v1/forms/instances/{instance_id}/attachments"
    label: {zh: "复用三态白名单（草稿/待补件可传，审批中不可传）", en: "Reuses state whitelist"}
  - kind: call
    to: oa.form.template.attachment.storage-access
    from_api: "POST /api/v1/forms/instances/{instance_id}/attachments"
    label: {zh: "落盘到私有存储", en: "Persists into private storage"}
---

## 证据锚点
- `doc/forms.md` → `### 1.4 附件通用限制`（§1.4 附件通用限制）
- `doc/enums.md` → `### 12.1 允许格式（15 种）`（§12.1 允许格式）
- `doc/enums.md` → `### 12.2 禁止格式（9 种，上传即拒绝）`（§12.2 禁止格式）
- `doc/templates.md` → `### 2.5 服务端校验要求（不可省略）`（§2.5：rules 必须由服务端执行；filePolicy 的参数表见 §2.3）
