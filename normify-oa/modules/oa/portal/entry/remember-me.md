---
uid: 59e77fa8
id: oa.portal.entry.remember-me
parent: oa.portal.entry
state: planned
name: {zh: "记住我免登录", en: "Remember Me"}
description:
  zh: >
      登录页「记住我」：勾选后 7 天内免登录，期间每次访问自动续期；未勾选时关闭浏览器即失效（REQ-USER-002）；登录态实现（Token 类型与有效期参数）由技术方案决定，本模块只约束用户可感知行为。
      
  en: >
      The Remember me option on the sign-in page: when ticked the user skips sign-in for seven days and each visit renews the window; when unticked the session dies as soon as the browser closes (REQ-USER-002); the token type and lifetime parameters are an engineering decision, this module only fixes the user-visible behaviour.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.281Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/entry/remember-me"
    description:
      zh: >
          开启记住我：7 天内免登录，每次访问自动续期。
          
      en: >
          Enable remember-me: seven days without signing in, auto-renewed on each visit.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/portal/entry/remember-me"
    description:
      zh: >
          关闭记住我并立即失效持久化凭据。
          
      en: >
          Turn remember-me off and drop the persistent token immediately.
          
deps:
  - kind: call
    to: oa.identity.session
    from_api: "POST /api/v1/portal/entry/remember-me"
    label: {zh: "签发与续期会话", en: "Issue & renew session"}
  - kind: event
    to: oa.audit.security
    from_api: "POST /api/v1/portal/entry/remember-me"
    label: {zh: "记录登录与续期日志", en: "Log login & renewal"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-002`（§6.8 移动端 H5 与登录保持）
