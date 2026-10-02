---
uid: "22725311"
id: oa.form.template.attachment.upload-policy
parent: oa.form.template.attachment
state: planned
name: {zh: "上传策略与格式白名单", en: "Upload Policy"}
description:
  zh: >
      单文件 ≤ 50 MB、单次上传 ≤ 20 个、单张单据附件总数 ≤ 50 个（含补件）；允许 pdf/doc/docx/wps/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/zip/rar/7z（V0.4 放行 wps 与 heic：heic 转 jpg 预览、wps 提示下载查看），禁止 exe/bat/cmd/js/vbs/ps1/dll/msi/scr，服务端按扩展名与 MIME 双重判断，上传即拒绝。
      
  en: >
      ≤50MB per file, ≤20 files per upload, ≤50 attachments per document including supplements; allows pdf/doc/docx/wps/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/zip/rar/7z (V0.4 adds wps and heic - heic is converted to jpg for preview, wps is download-only), and rejects exe/bat/cmd/js/vbs/ps1/dll/msi/scr on both extension and MIME checks at upload time.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.677Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 51
    end_line: 57
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/attachments/policy-check"
    description:
      zh: >
          校验附件扩展名、MIME、大小与数量。
          
      en: >
          Validates attachment extension, MIME, size and count.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/attachments/policy"
    description:
      zh: >
          读取附件限制策略（格式白名单与上限）。
          
      en: >
          Reads the attachment policy (format whitelist and limits).
          
---
