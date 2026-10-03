---
uid: 1e9849c5
id: oa.portal.initiate.upload
parent: oa.portal.initiate
state: planned
name: {zh: "附件上传块", en: "Attachment Upload Block"}
description:
  zh: >
      块状上传按钮 + 容量说明（「上限 30 个文件，最大 500MB/个」），不用大面积虚线拖拽区；已上传文件以列表行展示（图标 + 文件名 + 大小 + 删除），不做图片大图预览格子；超限时提示明确上限数值；补件附件标注轮次。
      
  en: >
      A block-style upload button with capacity copy (at most 30 files, 500MB each) instead of a large dashed drop zone; uploaded files appear as list rows (icon, name, size, delete) with no image thumbnail grid; over-limit uploads state the exact cap, and supplement attachments carry their round number.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.417Z"
fingerprint: 88c368e042714c6c1aa4b765a97c2bda96929b19c7be8666e8f9f305cf305896
source:
  - path: "DESIGN.md"
    line: 867
    end_line: 867
  - path: "DESIGN.md"
    line: 853
    end_line: 853
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/initiate/attachments"
    description:
      zh: >
          上传附件（类型与大小白名单校验）。
          
      en: >
          Upload an attachment with type and size allow-list checks.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/portal/initiate/attachments/{attachment_id}"
    description:
      zh: >
          删除未提交单据的附件。
          
      en: >
          Delete an attachment of an unsubmitted document.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/portal/initiate/attachments"
    label: {zh: "附件挂载到流程实例", en: "Attach file to flow instance"}
  - kind: dataflow
    to: oa.form.template.attachment.storage-access
    from_api: "POST /api/v1/portal/initiate/attachments"
    to_api: "mysql:flow_attachment"
    label: {zh: "附件存取与补件轮次", en: "Attachment storage access"}
---

## 证据锚点
- `doc/forms.md` → `### 1.4 附件通用限制`（§1.4 附件通用限制）
