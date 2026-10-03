---
uid: 4a4ab91e
id: oa.sign.record.file-integrity
parent: oa.sign.record
state: planned
name: {zh: "文件完整性与私有存储", en: "File Integrity & Private Storage"}
description:
  zh: >
      签名图与单据附件的文件完整性：私有化本地存储不存公网、下载必经鉴权禁止直链；上传前置校验单文件 ≤50MB、单次 ≤20 个、单张单据 ≤50 个（含补件）、白名单格式，禁止 exe/bat/cmd/js/vbs/ps1/dll/msi/scr（扩展名 + MIME 双重判断），并计算 sha256 防替换。
      
  en: >
      File integrity for signature images and attachments: private local storage, authenticated download only, upload guard of 50MB per file, 20 per batch and 50 per document including supplements, whitelist formats with executables blocked by extension and MIME, plus sha256 anti-substitution.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.589Z"
fingerprint: 6f9b9bacc011821946c25a4eb2a23a9ade7198597008d6734999c7f0c77cde96
source:
  - path: "doc/forms.md"
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "storage/signatures/{user_id}/{id}.png"
    description:
      zh: >
          签名图私有化本地存储路径（禁止公网直链）。
          
      en: >
          Private local storage path of signature images, never publicly linked.
          
  - protocol: http
    method: POST
    path: "/api/v1/attachments/validate"
    description:
      zh: >
          附件上传前置校验：大小、数量、格式白名单与禁止格式（扩展名 + MIME）。
          
      en: >
          Pre-upload validation of size, count and format whitelist with forbidden types by extension and MIME.
          
  - protocol: http
    method: POST
    path: "/api/v1/instances/{instance_id}/attachments"
    description:
      zh: >
          上传附件：单次 ≤20 个、单张单据 ≤50 个（含补件），带 round 轮次标记。
          
      en: >
          Uploads attachments: at most 20 per batch and 50 per document including supplements, tagged with a round.
          
  - protocol: http
    method: GET
    path: "/api/v1/attachments/{id}/download"
    description:
      zh: >
          鉴权下载附件（禁止直链，校验数据域可见性）。
          
      en: >
          Authenticated attachment download with data-scope checks, never a public link.
          
  - protocol: http
    method: GET
    path: "/api/v1/attachments/{id}/integrity"
    description:
      zh: >
          校验附件 sha256，防止文件被替换。
          
      en: >
          Verifies the attachment sha256 against substitution.
          
deps:
  - kind: reference
    to: oa.form.template
    label: {zh: "表单通用附件限制契约", en: "Shared attachment limits"}
  - kind: reference
    to: oa.workflow.supplement
    label: {zh: "补件轮次 round 1..3", en: "Supplement round 1..3"}
  - kind: reference
    to: oa.authz.visibility
    label: {zh: "下载鉴权与可见性", en: "Download auth & visibility"}
---

## 证据锚点
- `doc/forms.md` → `### 1.4 附件通用限制`（§1.4 附件通用限制）
- `doc/data-model.md` → `CREATE TABLE flow_attachment`（§6. 签名、附件、抄送、消息、审计）
- `doc/prd-0.1.md` → `REQ-FORM-002`（§3.1 本期做）
