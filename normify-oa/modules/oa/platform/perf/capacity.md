---
uid: a236b39e
id: oa.platform.perf.capacity
parent: oa.platform.perf
state: planned
name: {zh: "容量口径与压测", en: "Capacity Baseline"}
description:
  zh: >
      按已澄清口径（总用户 300+、峰值在线 80、并发审批 30 TPS）建立容量基线，并用压测场景验证在该规模下仍满足响应要求。
      
  en: >
      Capacity baseline derived from the agreed figures (300+ total users, 80 peak online, 30 approval operations per second) with load-test scenarios that hold under those numbers.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.269Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "benchmarks/capacity/approval-30tps.jmx"
    description:
      zh: >
          并发审批压测场景脚本。
          
      en: >
          Load-test scenario for concurrent approvals.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/perf/baseline"
    description:
      zh: >
          查询容量基线与压测结论。
          
      en: >
          Reads the capacity baseline and load-test conclusions.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-003`（§第9章 非功能需求）
