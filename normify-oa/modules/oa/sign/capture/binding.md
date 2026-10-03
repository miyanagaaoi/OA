---
uid: 148d14e4
id: oa.sign.capture.binding
parent: oa.sign.capture
name: {zh: "签名与审批动作绑定", en: "Signature Binding & Forensics"}
description:
  zh: >
      将签名与具体审批动作绑定：签名时间戳由服务端生成（不信任客户端时间），同时采集设备指纹（浏览器指纹）、IP 与 User-Agent 作为取证信息，与签名图一并写入签名记录，用于责任认定。
      
  en: >
      Binds a signature to a specific approval act: the timestamp is server-generated while device/browser fingerprint, IP and User-Agent are captured as forensic evidence alongside the image.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.351Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/sign/tasks/{task_id}/bind"
    description:
      zh: >
          把签名绑定到审批任务，时间戳由服务端生成。
          
      en: >
          Binds a signature to an approval task with a server-generated timestamp.
          
  - protocol: http
    method: POST
    path: "/api/v1/sign/capture/fingerprint"
    description:
      zh: >
          采集设备指纹（浏览器指纹）与 IP / User-Agent。
          
      en: >
          Captures the device/browser fingerprint plus IP and User-Agent.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/capture/evidence/{signature_id}"
    description:
      zh: >
          查询某条签名记录的取证信息。
          
      en: >
          Reads the forensic evidence of one signature record.
          
deps:
  - kind: call
    to: oa.sign.record.append
    from_api: "POST /api/v1/sign/tasks/{task_id}/bind"
    to_api: "POST /api/v1/sign/records"
    label: {zh: "写入签名记录", en: "Persist signature record"}
  - kind: reference
    to: oa.workflow.task
    label: {zh: "绑定的审批任务对象", en: "Bound approval task"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-004`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
