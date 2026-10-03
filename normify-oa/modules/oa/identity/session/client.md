---
uid: 4072b0d7
id: oa.identity.session.client
parent: oa.identity.session
name: {zh: "客户端登录承载与兼容", en: "Client Login & Compatibility"}
description:
  zh: >
      客户端登录承载：首页二维码/短链入口票据换取登录态；浏览器兼容范围为 Chrome/Edge/Safari 主流版本，微信内置浏览器不作为验收目标但须保证可登录可审批不崩溃。
      
  en: >
      Client-side login carriage: QR-code and short-link tickets are exchanged for a session; the compatibility matrix is mainstream Chrome/Edge/Safari, and the WeChat built-in browser must at least stay crash-free and able to log in and approve.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.238Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "doc/prd-0.1.md"
  - path: "DESIGN.md"
    line: 1144
    end_line: 1152
apis:
  - protocol: http
    method: GET
    path: "/api/v1/auth/client-config"
    description:
      zh: >
          返回客户端登录承载与兼容能力配置。
          
      en: >
          Returns client login-carriage and compatibility config.
          
  - protocol: http
    method: POST
    path: "/api/v1/auth/entry-exchange"
    description:
      zh: >
          二维码/短链入口票据换取登录态。
          
      en: >
          Exchanges an entry ticket for a session.
          
deps:
  - kind: call
    to: oa.identity.session.login.issue
    from_api: "POST /api/v1/auth/entry-exchange"
    to_api: "POST /api/v1/auth/login"
    label: {zh: "入口票据换取会话", en: "Exchange ticket for session"}
  - kind: reference
    to: oa.portal.entry
    label: {zh: "二维码与短链入口由门户首页承载", en: "QR and short-link entry"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-005`（§6.8 移动端 H5 与登录保持）
