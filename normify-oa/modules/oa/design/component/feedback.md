---
uid: aa9db00e
id: oa.design.component.feedback
parent: oa.design.component
state: planned
name: {zh: "反馈与浮层", en: "Feedback & Overlays"}
description:
  zh: >
      反馈族：modal（居中，560 / 400 / 720px 三档宽度，确认按钮文案要具体）、drawer（右侧 480px / 日志 640px）、notification-toast（右上 360px，左侧 3px 状态色竖条，成功 3 秒自动消失、失败不自动消失并给重试）、watermark、signature-pad 与 signature-stamp（签名图不可删除，仅可重新签署）。
      
  en: >
      The feedback family: modal (centred, 560 / 400 / 720px wide, with a specific confirm label), drawer (480px on the right, 640px for logs), notification-toast (360px top-right with a 3px status bar on the left, auto-dismissed after three seconds on success but persistent with retry on failure), watermark, signature-pad and signature-stamp (a signature image can never be deleted, only re-signed).
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.700Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 895
    end_line: 902
  - path: "DESIGN.md"
    line: 937
    end_line: 937
apis:
  - protocol: file
    path: "styles/components/feedback.css"
    description:
      zh: >
          反馈类样式：模态与抽屉浮层、Toast、水印与签名面板。
          
      en: >
          Feedback CSS: modal and drawer overlays, toast, watermark and signature surfaces.
          
deps:
  - kind: reference
    to: oa.design.token.elevation
    from_api: "file:styles/components/feedback.css"
    to_api: "file:styles/tokens/elevation.css"
    label: {zh: "层级 3–5 投影", en: "Layer 3-5 shadows"}
  - kind: reference
    to: oa.notify.inbox
    from_api: "file:styles/components/feedback.css"
    label: {zh: "流程结果走站内信", en: "Inbox for flow results"}
---
