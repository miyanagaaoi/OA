---
uid: 55760c71
id: oa.sign.ca.roadmap
parent: oa.sign.ca
state: planned
name: {zh: "CA 接入演进预留", en: "CA Integration Roadmap"}
description:
  zh: >
      二期引入第三方 CA 所需的交互协议位与能力开关：一期仅新增「CA 签署/验签」交互，不改动数据模型、哈希范围与审计链路；能力开关默认关闭，避免一期误触发。
      
  en: >
      Capability switches and protocol slots for introducing a third-party CA in phase two: only the sign/verify interaction is added, leaving the data model, hash scope and audit chain untouched.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.409Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/sign/ca/capabilities"
    description:
      zh: >
          CA 能力开关，一期全部关闭。
          
      en: >
          CA capability switches, all off in phase one.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/ca/protocol"
    description:
      zh: >
          二期 CA 签署/验签交互协议位说明。
          
      en: >
          Protocol slots for phase-two CA signing and verification.
          
deps:
  - kind: reference
    to: oa.integration.gateway
    label: {zh: "二期 CA 经网关接入", en: "Phase-two CA via gateway"}
  - kind: reference
    to: oa.platform.security
    label: {zh: "证书与密钥安全策略", en: "Certificate & key policy"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-005`（§6.5 电子签名与身份确认）
