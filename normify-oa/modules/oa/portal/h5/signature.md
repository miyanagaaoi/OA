---
uid: 499ce772
id: oa.portal.h5.signature
parent: oa.portal.h5
state: planned
name: {zh: "H5 手写签名面板", en: "H5 Signature Pad"}
description:
  zh: >
      手写签名面板：H5 自适应宽度 × 高 200px（桌面 640×200），canvas-subtle 底 + 1px 虚线边框 + rounded.sm；上方提示「请在框内签名」，下方为「清除」「使用预存签名」「确认签名」；线条 2px 墨色圆头平滑曲线，未签名时「确认」禁用。
      
  en: >
      The handwritten signature pad: 200px tall and full width on H5 (640×200 on desktop) on a canvas-subtle fill with a 1px dashed border and rounded.sm; the prompt Please sign inside the box sits above, with Clear, Use preset signature and Confirm signature below; strokes are 2px ink round-capped smooth curves and Confirm stays disabled until something is drawn.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.643Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "DESIGN.md"
    line: 901
    end_line: 902
  - path: "doc/prd-0.1.md"
    line: 365
    end_line: 368
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/h5/signatures"
    description:
      zh: >
          提交手写签名图（绑定设备指纹与 IP）。
          
      en: >
          Submit a handwritten signature image bound to device fingerprint and IP.
          
  - protocol: http
    method: GET
    path: "/api/v1/portal/h5/signatures/preset"
    description:
      zh: >
          调用用户预存签名（一键复用）。
          
      en: >
          Load the user's preset signature for one-tap reuse.
          
deps:
  - kind: call
    to: oa.sign.capture
    from_api: "POST /api/v1/portal/h5/signatures"
    label: {zh: "存入手写签名记录", en: "Store handwritten signature"}
  - kind: call
    to: oa.sign.preset
    from_api: "GET /api/v1/portal/h5/signatures/preset"
    label: {zh: "读取预存签名", en: "Load preset signature"}
---
