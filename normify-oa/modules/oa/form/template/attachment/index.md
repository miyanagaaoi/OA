---
uid: 2201602b
id: oa.form.template.attachment
parent: oa.form.template
name: {zh: "附件通用限制", en: "Shared Attachment Rules"}
description:
  zh: >
      附件统一规则：单文件 ≤50MB、单次上传 ≤20 个、单张单据 ≤50 个（含补件）、15 种允许格式、9 种禁止格式（扩展名 + 声明 MIME + 内容嗅探三重判断）；私有化本地存储、鉴权下载、禁止直链；补件轮次标记。上传/下载/删除一律由服务端强制，附件不是绕过三态白名单的写入通道。
      
  en: >
      Shared attachment rules: ≤50MB per file, ≤20 files per upload, ≤50 per document including supplements, 15 allowed formats, 9 forbidden formats (extension plus declared MIME plus content sniffing); private on-premise storage with authorized downloads and no direct links; supplement round marking. Upload/download/delete are all server-enforced - attachments are never a write channel that bypasses the three-state whitelist.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.284Z"
fingerprint: 44ff60f59106640ffba662c0760e27077ca5b805cd336a61dfb7068a60fd999e
source:
  - path: "doc/forms.md"
  - path: "doc/enums.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/test-cases.md"
---

## 证据锚点
- `doc/forms.md` → `### 1.4 附件通用限制`（§1.4 附件通用限制）
- `doc/enums.md` → `## 12. 附件格式与轮次`（§12 附件格式与轮次）
- `doc/prd-0.1.md` → `REQ-FORM-002`（§8.1 需求定义）
- `doc/test-cases.md` → `### 4.4 附件与鉴权下载（FORM）`（§4.4 附件与鉴权下载）
