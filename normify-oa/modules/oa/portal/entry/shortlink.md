---
uid: 592ca46e
id: oa.portal.entry.shortlink
parent: oa.portal.entry
state: planned
name: {zh: "移动端短链", en: "Mobile Short Link"}
description:
  zh: >
      移动端短链：把 H5 入口地址压缩为短链供二维码与线下张贴使用，支持有效期与访问统计；短链跳转不携带业务数据，落地后按登录态决定进入登录页或待办列表。
      
  en: >
      The mobile short link compresses the H5 entry address for QR codes and printed notices, with an expiry and visit statistics; the redirect carries no business data, and after landing the login state decides whether the user sees the sign-in page or the pending list.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.771Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/entry/shortlinks"
    description:
      zh: >
          创建 H5 入口短链（含有效期与访问统计）。
          
      en: >
          Create a short link for the H5 entry with expiry and visit statistics.
          
  - protocol: http
    method: GET
    path: "/s/{code}"
    description:
      zh: >
          短链跳转至 H5 门户（不携带业务数据）。
          
      en: >
          Short link redirect to the H5 portal (carries no business data).
          
deps:
  - kind: call
    to: oa.integration.gateway
    from_api: "POST /api/v1/portal/entry/shortlinks"
    label: {zh: "入口流量经统一网关", en: "Entry traffic via gateway"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-001`（§6.8 移动端 H5 与登录保持）
