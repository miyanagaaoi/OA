---
uid: 0131b18b
id: oa.sign.capture.stroke
parent: oa.sign.capture
state: planned
name: {zh: "笔迹渲染与签名图生成", en: "Stroke Rendering"}
description:
  zh: >
      把笔迹点序列规范化渲染为 PNG 签名图（固定画布、透明底、平滑去抖），写入私有化本地存储并计算文件哈希；提供确认前的签名图预览。
      
  en: >
      Normalizes stroke points into a PNG signature image (fixed canvas, transparent background, smoothing), writes it to private local storage with a file hash, and offers a preview before confirmation.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.664Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/sign/capture/render"
    description:
      zh: >
          将笔迹点序列渲染为规范化 PNG 签名图。
          
      en: >
          Renders stroke points into a normalized PNG signature image.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/capture/render/{render_token}/preview"
    description:
      zh: >
          预览本次生成的签名图（鉴权后返回）。
          
      en: >
          Previews the generated signature image behind authentication.
          
deps:
  - kind: call
    to: oa.sign.record.append
    from_api: "POST /api/v1/sign/capture/render"
    to_api: "POST /api/v1/sign/records"
    label: {zh: "写入签名记录", en: "Persist signature record"}
  - kind: reference
    to: oa.sign.record.file-integrity
    to_api: "file:storage/signatures/{user_id}/{id}.png"
    label: {zh: "私有化存储与鉴权下载", en: "Private storage & download"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-001`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
