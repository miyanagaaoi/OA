---
uid: 277ca5ca
id: oa.sign.preset.audit
parent: oa.sign.preset
state: planned
name: {zh: "预存签名修改留痕", en: "Preset Signature Audit Trail"}
description:
  zh: >
      预存签名的上传、替换与删除全部留痕（REQ-SIGN-002）：发布变更事件并保留变更历史，供个人中心与审计查询，防止事后否认预存签名内容。
      
  en: >
      Every upload, replacement and deletion of a preset signature is traced (REQ-SIGN-002) through change events and history, queryable from the personal center and by audit.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.297Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: kafka
    path: "oa.sign.preset.changed"
    description:
      zh: >
          预存签名新增/修改/删除事件（含变更前后）。
          
      en: >
          Preset signature created/changed/deleted event with before and after values.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/presets/{id}/history"
    description:
      zh: >
          查询某张预存签名的变更历史。
          
      en: >
          Reads the change history of one preset signature.
          
deps:
  - kind: dataflow
    to: oa.audit.oplog
    label: {zh: "变更留痕写入审计日志", en: "Write change to audit log"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-002`（§6.5 电子签名与身份确认）
