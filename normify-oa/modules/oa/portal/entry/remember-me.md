---
uid: 59e77fa8
id: oa.portal.entry.remember-me
parent: oa.portal.entry
name: {zh: "记住我免登录", en: "Remember Me"}
description:
  zh: >
      登录页「记住我」：勾选后 7 天内免登录，期间每次访问自动续期；未勾选时关闭浏览器即失效（REQ-USER-002）；登录态实现（Token 类型与有效期参数）由技术方案决定，本模块只约束用户可感知行为。
      
  en: >
      The Remember me option on the sign-in page: when ticked the user skips sign-in for seven days and each visit renews the window; when unticked the session dies as soon as the browser closes (REQ-USER-002); the token type and lifetime parameters are an engineering decision, this module only fixes the user-visible behaviour.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.109Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
