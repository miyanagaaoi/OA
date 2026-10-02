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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.741Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 415
    end_line: 415
  - path: "doc/prd-0.1.md"
    line: 637
    end_line: 637
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
