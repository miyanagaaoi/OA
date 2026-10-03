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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.613Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-002`（§6.8 移动端 H5 与登录保持）
