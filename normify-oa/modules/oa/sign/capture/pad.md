---
uid: 00caf7fd
id: oa.sign.capture.pad
parent: oa.sign.capture
state: planned
name: {zh: "签名面板交互", en: "Signature Pad UI"}
description:
  zh: >
      移动端 H5 与桌面端的签名面板：审批人点击「签名确认」后弹出画布，支持触屏手写、鼠标书写、撤销与清空重写，采集笔迹点序列（含时间与压感）后交由笔迹渲染模块生成签名图。
      
  en: >
      The signature pad for mobile H5 and desktop: tapping sign-confirm opens a canvas supporting touch and mouse drawing, undo and clear; stroke points (with time and pressure) are captured and handed to the stroke renderer.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.785Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 365
    end_line: 365
  - path: "doc/prd-0.1.md"
    line: 415
    end_line: 419
apis:
  - protocol: http
    method: POST
    path: "/api/v1/sign/capture/sessions"
    description:
      zh: >
          打开签名面板会话，返回画布尺寸与采集参数。
          
      en: >
          Opens a signature pad session and returns canvas size and capture parameters.
          
  - protocol: http
    method: PUT
    path: "/api/v1/sign/capture/sessions/{session_id}/strokes"
    description:
      zh: >
          提交手写笔迹点序列（触屏或鼠标）。
          
      en: >
          Submits handwritten stroke points (touch or mouse).
          
  - protocol: http
    method: POST
    path: "/api/v1/sign/capture/sessions/{session_id}/confirm"
    description:
      zh: >
          确认签名并触发生成签名图。
          
      en: >
          Confirms the signature and triggers image generation.
          
deps:
  - kind: call
    to: oa.sign.capture.stroke
    from_api: "POST /api/v1/sign/capture/sessions/{session_id}/confirm"
    to_api: "POST /api/v1/sign/capture/render"
    label: {zh: "笔迹渲染为签名图", en: "Render strokes to image"}
  - kind: call
    to: oa.sign.capture.policy
    from_api: "POST /api/v1/sign/capture/sessions"
    to_api: "POST /api/v1/sign/capture/requirements/resolve"
    label: {zh: "读取节点签名要求", en: "Resolve node sign policy"}
  - kind: call
    to: oa.sign.preset.picker
    from_api: "POST /api/v1/sign/capture/sessions"
    to_api: "GET /api/v1/sign/presets/picker"
    label: {zh: "调用预存签名", en: "Use preset signature"}
---
