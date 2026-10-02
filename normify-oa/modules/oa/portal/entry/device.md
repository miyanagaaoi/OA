---
uid: 5d666149
id: oa.portal.entry.device
parent: oa.portal.entry
state: planned
name: {zh: "多设备登录上限", en: "Multi-device Limit"}
description:
  zh: >
      多设备登录（REQ-USER-003）：同一账号同时在线设备数上限可配置（默认 3 台），超出时踢出最早登录的设备并提示原因；被踢出设备的会话立即失效，踢出动作写入登录日志。
      
  en: >
      Multi-device sign-in (REQ-USER-003): the number of concurrently online devices per account is configurable with a default of three; exceeding it kicks out the device that signed in earliest with an explanation, the kicked session dies at once and every kick is written to the login log.
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:04:46.293Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 417
    end_line: 417
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/entry/devices"
    description:
      zh: >
          列出账号同时在线设备及其登录时间与地址。
      en: >
          List a user's concurrently signed-in devices with login time and address.
  - protocol: http
    method: DELETE
    path: "/api/v1/portal/entry/devices/{device_id}"
    description:
      zh: >
          踢出指定设备，其会话立即失效。
      en: >
          Kick one device; its session dies immediately.
deps:
  - kind: call
    to: oa.identity.session
    from_api: "GET /api/v1/portal/entry/devices"
    label: {zh: "会话设备列表与踢出", en: "List & kick sessions"}
  - kind: dataflow
    to: oa.identity.user.profile
    from_api: "GET /api/v1/portal/entry/devices"
    to_api: "mysql:sys_user"
    label: {zh: "账号与设备上限配置", en: "Account & device cap"}
  - kind: event
    to: oa.audit.security
    from_api: "DELETE /api/v1/portal/entry/devices/{device_id}"
    label: {zh: "踢出设备写入登录日志", en: "Log device kick-out"}
---
