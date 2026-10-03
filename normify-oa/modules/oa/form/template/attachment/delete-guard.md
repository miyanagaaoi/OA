---
uid: 4c1f9d02
id: oa.form.template.attachment.delete-guard
parent: oa.form.template.attachment
name: {zh: "附件删除与三态窗口", en: "Attachment Deletion Guard"}
description:
  zh: >
      删除附件的两条闸门：**身份**（仅上传者本人或系统管理员，其余 40310）与**状态窗口**（复用三态白名单，草稿/待补件可删，审批中与已完结 40304，不给「先删后传」的写旁路）。元数据与物理文件的删除顺序为**先删文件、后删元数据**：文件删除失败即 50004 并回滚事务（元数据保留、可重试），因此不可能出现无人引用却占空间的孤儿文件；元数据删除失败则留下悬挂行，下载按 404 fail-closed。
      
  en: >
      Two gates for deleting an attachment: identity (only the uploader or a system administrator, otherwise 40310) and the state window (the three-state whitelist is reused; deletable in draft and pending-supplement, 40304 while approving or closed). The physical file is removed before the metadata row: a failed file delete raises 50004 and rolls the transaction back (metadata kept, retry possible), so an unreferenced file can never linger.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.455Z"
fingerprint: ae86cc3ec8cfd217e9edc2d09edef921a87215feecd71c217f1f2fda6d633285
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/attachment/app/AttachmentService.java"
  - path: "oa-server/src/main/java/com/oa/form/attachment/api/AttachmentController.java"
apis:
  - protocol: http
    method: DELETE
    path: "/api/v1/forms/attachments/{attachment_id}"
    description:
      zh: >
          删除附件（仅上传者本人或管理员，且仅在草稿/待补件窗口内）：先删物理文件再删元数据行。
          
      en: >
          Deletes an attachment (uploader or administrator only, and only within the draft/pending-supplement window): the physical file is removed first, then the metadata row.
          
deps:
  - kind: call
    to: oa.form.template.write-model.state-whitelist
    from_api: "DELETE /api/v1/forms/attachments/{attachment_id}"
    label: {zh: "复用三态白名单作为删除窗口", en: "Reuses state whitelist window"}
---

## 证据锚点
- `doc/forms.md` → `### 1.2 字段的三态读写模型（**核心约束**）`（§1.2 三态读写模型：附件只在草稿与待补件可写）
