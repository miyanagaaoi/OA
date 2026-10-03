---
uid: 4c1f9d02
id: oa.form.template.attachment.delete-guard
parent: oa.form.template.attachment
name: {zh: "附件删除与三态窗口", en: "Attachment Deletion Guard"}
description:
  zh: >
      删除附件的两条闸门：**身份**（上传者本人 ∪ 单据发起人本人 ∪ 系统管理员，其余 40310「无权删除该附件：仅上传者本人、单据发起人本人或系统管理员可删除」）与**状态窗口**（复用三态白名单，草稿/待补件可删，审批中与已完结 40304，不给「先删后传」的写旁路）。「发起人本人」是代传场景的补丁：管理员代传时 uploader_id＝管理员，只认上传者会让发起人反而删不掉自己单据的附件。删除顺序为先删文件、后删元数据：文件删除失败即 50004 并回滚（元数据保留、可重试）。
      
  en: >
      Two gates for deleting an attachment: identity (uploader, document initiator, or system administrator; else 40310) and the state window (three-state whitelist reused; deletable in draft and pending-supplement, 40304 while approving or closed). The initiator tier covers delegated uploads, where uploader_id is the admin. The file is deleted before the metadata row: a failed file delete raises 50004 and rolls back (metadata kept, retryable).
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.035Z"
fingerprint: d4d66fddc36c266d7ab9fbbd808f6b3802ea345dd1badc4fce143b7e4485e3ec
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
          删除附件（上传者本人 ∪ 单据发起人本人 ∪ 系统管理员，且仅在草稿/待补件窗口内）：先删物理文件再删元数据行。
          
      en: >
          Deletes an attachment (uploader, document initiator, or system administrator, and only within the draft/pending-supplement window): the physical file is removed first, then the metadata row.
          
deps:
  - kind: call
    to: oa.form.template.write-model.state-whitelist
    from_api: "DELETE /api/v1/forms/attachments/{attachment_id}"
    label: {zh: "复用三态白名单作为删除窗口", en: "Reuses state whitelist window"}
---

## 证据锚点
- `doc/forms.md` → `### 1.2 字段的三态读写模型（**核心约束**）`（§1.2 三态读写模型：附件只在草稿与待补件可写）
