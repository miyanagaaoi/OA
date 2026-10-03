---
uid: 2750e01a
id: oa.form.template.attachment.storage-access
parent: oa.form.template.attachment
state: planned
name: {zh: "私有存储与鉴权下载", en: "Private Storage & Authorized Download"}
description:
  zh: >
      附件私有化本地存储、不存公网；下载必须经鉴权接口，禁止直链；元数据落 `flow_attachment`，实体按 storage/attachments/{instance_id}/{round}/{file} 组织。
      
  en: >
      Attachments live in private on-premise storage, never on the public internet; downloads must pass an authorized endpoint with no direct links; metadata lands in `flow_attachment` and files are laid out under storage/attachments/{instance_id}/{round}/{file}.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.216Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/instances/{instance_id}/attachments"
    description:
      zh: >
          登记已上传的附件元数据。
          
      en: >
          Registers an uploaded attachment's metadata.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/attachments/{attachment_id}/download"
    description:
      zh: >
          鉴权下载附件，禁止直链。
          
      en: >
          Downloads an attachment after authorization; direct links are forbidden.
          
  - protocol: mysql
    path: "flow_attachment"
    description:
      zh: >
          附件元数据表（路径、轮次、大小、MIME）。
          
      en: >
          Attachment metadata table (path, round, size, MIME).
          
  - protocol: file
    path: "storage/attachments/{instance_id}/{round}/{file}"
    description:
      zh: >
          附件实体存储路径。
          
      en: >
          Physical attachment storage path.
          
deps:
  - kind: reference
    to: oa.platform.security
    from_api: "GET /api/v1/forms/attachments/{attachment_id}/download"
    label: {zh: "私有化部署与存储安全", en: "On-premise storage security"}
---

## 证据锚点
- `doc/forms.md` → `### 1.4 附件通用限制`（§1.4 附件通用限制）
