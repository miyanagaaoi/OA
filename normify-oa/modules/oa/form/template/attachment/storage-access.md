---
uid: 2750e01a
id: oa.form.template.attachment.storage-access
parent: oa.form.template.attachment
name: {zh: "私有存储与鉴权下载", en: "Private Storage & Authorized Download"}
description:
  zh: >
      附件落**私有本地目录**（默认 ${user.home}/.oa/attachments，仓库之外；可用 oa.attachment.root 覆盖），按 年/月/日/随机名 分片，先写 .part 再原子改名；落库的是相对路径。工程**不注册任何静态资源映射**，因此 storage_path 没有可达 URL（直链 404）。下载/预览必须过鉴权接口 + 数据域（受控表 flow_attachment，每条 SELECT 带 @dataScope 标记、过滤主体恒为 flow_instance），域外与不存在同样 404；下载恒为 attachment，仅 image/jpeg、image/png、application/pdf 允许 inline。
      
  en: >
      Attachments live in a private dir (default ~/.oa/attachments, outside the repo; override oa.attachment.root), sharded by yyyy/MM/dd plus a random name, atomically renamed from a .part file; only the relative path is stored. No static mapping exists, so storage_path has no reachable URL. Download and preview need auth plus the data scope (controlled table flow_attachment, one @dataScope marker per SELECT, filtered via flow_instance). Downloads are always attachment; only jpeg/png/pdf may inline.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.458Z"
fingerprint: a26aa8ca604ec190735d2a8ab67b7b57db287a455a984e8f4560305edecb3256
source:
  - path: "doc/forms.md"
  - path: "doc/data-model.md"
  - path: "oa-server/src/main/java/com/oa/form/attachment/app/AttachmentStorage.java"
  - path: "oa-server/src/main/java/com/oa/form/attachment/app/LocalAttachmentStorage.java"
  - path: "oa-server/src/main/java/com/oa/form/attachment/api/AttachmentController.java"
  - path: "oa-server/src/main/java/com/oa/form/attachment/infra/AttachmentMapper.java"
  - path: "oa-server/src/main/resources/mapper/form/AttachmentMapper.xml"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/attachments/{attachment_id}/download"
    description:
      zh: >
          鉴权下载附件，恒为 Content-Disposition: attachment，禁止直链。
          
      en: >
          Downloads an attachment after authorization, always Content-Disposition: attachment; direct links are forbidden.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/attachments/{attachment_id}/preview"
    description:
      zh: >
          预览：仅 image/jpeg、image/png、application/pdf 内联，其余降级为下载。
          
      en: >
          Preview: inline only for image/jpeg, image/png and application/pdf; everything else degrades to download.
          
  - protocol: mysql
    path: "flow_attachment"
    description:
      zh: >
          附件元数据表（相对存储路径、轮次、大小、扩展名、MIME、sha256、上传人）。
          
      en: >
          Attachment metadata table (relative storage path, round, size, extension, MIME, sha256, uploader).
          
  - protocol: file
    path: "oa.attachment.root/{yyyy}/{MM}/{dd}/{random32}.{ext}"
    description:
      zh: >
          附件实体存储布局（服务端生成，客户端字符串不参与拼接）。
          
      en: >
          Physical attachment layout (server-generated; no client string is ever concatenated into it).
          
deps:
  - kind: reference
    to: oa.platform.security
    from_api: "GET /api/v1/forms/attachments/{attachment_id}/download"
    label: {zh: "私有化部署与存储安全", en: "On-premise storage security"}
  - kind: reference
    to: oa.form.template.write-model.state-whitelist
    from_api: "GET /api/v1/forms/attachments/{attachment_id}/download"
    label: {zh: "数据域 + 状态判定复用既有守卫", en: "Reuses scope and state guards"}
---

## 证据锚点
- `doc/forms.md` → `### 1.4 附件通用限制`（§1.4 附件通用限制）
- `doc/data-model.md` → `CREATE TABLE flow_attachment`（§6.2 附件）
