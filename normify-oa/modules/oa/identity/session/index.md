---
uid: 0b1f3c25
id: oa.identity.session
parent: oa.identity
name: {zh: "登录与会话", en: "Login & Session"}
description:
  zh: >
      登录与会话：密码复杂度（8 位以上含字母数字）、失败 5 次锁定 15 分钟、「记住我」7 天免登录并自动续期、多设备同时在线上限可配（默认 3 台，超出踢出最早登录设备）、登录日志。
      
  en: >
      Login with password complexity rules and 5-attempt lockout; remember-me sessions valid for seven days with automatic renewal; multi-device login with a configurable cap (default three, earliest device evicted); login logging.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.476Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-002`（§6.8 移动端 H5 与登录保持）
