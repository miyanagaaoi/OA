---
uid: a236b39e
id: oa.platform.perf.capacity
parent: oa.platform.perf
name: {zh: "容量口径与压测", en: "Capacity Baseline"}
description:
  zh: >
      按已澄清口径（总用户 300+、峰值在线 80、并发审批 30 TPS）建立容量基线，并用压测场景验证在该规模下仍满足响应要求。
      
  en: >
      Capacity baseline derived from the agreed figures (300+ total users, 80 peak online, 30 approval operations per second) with load-test scenarios that hold under those numbers.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.329Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
