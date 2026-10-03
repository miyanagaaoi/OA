---
uid: 4d062c8b
id: oa.portal.h5.compat
parent: oa.portal.h5
state: planned
name: {zh: "浏览器兼容与降级", en: "Browser Compatibility"}
description:
  zh: >
      浏览器兼容范围：Chrome / Edge / Safari 主流版本；明确不支持微信内置浏览器作为验收目标，但在微信中打开须保证基本可用（不崩溃、可登录、可审批）；令牌全部为标准 CSS 值、字体走系统回退，不依赖外网字体。
      
  en: >
      Supported browsers: current Chrome, Edge and Safari; the WeChat built-in browser is explicitly not an acceptance target, yet opening the site inside WeChat must stay basically usable (no crash, can log in, can approve); every token is a standard CSS value and fonts fall back to system faces so no external font request can break the page.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.772Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "doc/prd-0.1.md"
  - path: "DESIGN.md"
    line: 1153
    end_line: 1153
  - path: "DESIGN.md"
    line: 716
    end_line: 716
apis:
  - protocol: http
    method: GET
    path: "/m/portal/compat-check"
    description:
      zh: >
          客户端能力探测页（识别 iOS Safari 与微信内置浏览器）。
          
      en: >
          Client capability probe page that detects iOS Safari and the WeChat built-in browser.
          
  - protocol: http
    method: GET
    path: "/api/v1/portal/h5/capabilities"
    description:
      zh: >
          服务端下发的客户端能力开关（用于降级渲染）。
          
      en: >
          Server-declared client capability switches used to degrade the UI.
          
deps:
  - kind: reference
    to: oa.design.token
    label: {zh: "标准 CSS 值与字体回退", en: "Standard CSS & font fallbacks"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-005`（§6.8 移动端 H5 与登录保持）
