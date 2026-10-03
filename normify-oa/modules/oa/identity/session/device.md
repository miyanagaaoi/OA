---
uid: 37a8e5d6
id: oa.identity.session.device
parent: oa.identity.session
name: {zh: "多设备在线与上限", en: "Multi-device Sessions"}
description:
  zh: >
      同一账号可多设备登录，同时在线上限可配置（默认 3 台）；超出时踢出最早登录的设备，并列出在线设备供本人或管理员查看。
      
  en: >
      One account may log in from several devices with a configurable cap (three by default); exceeding it evicts the earliest session and the online device list stays visible to the user or an admin.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.663Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 413
    end_line: 420
  - path: "doc/prd-0.1.md"
    line: 546
    end_line: 561
apis:
  - protocol: http
    method: GET
    path: "/api/v1/auth/sessions"
    description:
      zh: >
          列出本人（或指定人）的在线设备。
          
      en: >
          Lists online devices of the caller or a given user.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/auth/sessions/{sessionId}"
    description:
      zh: >
          远程注销指定设备会话。
          
      en: >
          Revokes a given device session.
          
  - protocol: http
    method: PUT
    path: "/api/v1/auth/sessions/limit"
    description:
      zh: >
          配置同时在线设备上限。
          
      en: >
          Configures the concurrent device cap.
          
deps:
  - kind: call
    to: oa.identity.session.login.issue
    from_api: "DELETE /api/v1/auth/sessions/{sessionId}"
    to_api: "redis:auth:session:{token}"
    label: {zh: "踢出最早登录设备会话", en: "Evict earliest session"}
---
