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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.740Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 365
    end_line: 365
  - path: "doc/data-model.md"
    line: 535
    end_line: 563
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
