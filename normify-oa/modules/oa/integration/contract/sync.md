---
uid: 44e93dc2
id: oa.integration.contract.sync
parent: oa.integration.contract
name: {zh: "台账双向同步", en: "Contract Ledger Sync"}
description:
  zh: >
      二期：合同管理系统作为独立模块接入，复用 OA 用户体系与审批引擎，通过 API 实现合同台账与审批流双向同步。
      
  en: >
      Phase two: the contract system joins as a standalone module reusing OA identity and the approval engine, syncing the contract ledger and approval flow both ways over APIs.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.073Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/integration/contracts/sync"
    description:
      zh: >
          二期：与合同系统同步台账与审批状态。
          
      en: >
          Phase two: sync ledgers and approval status with the contract system.
          
  - protocol: kafka
    path: "oa.contract.approved"
    description:
      zh: >
          合同审批通过后对外发布的事件。
          
      en: >
          Event published when a contract is approved.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.2 合同管理系统集成路径`（§8.2 合同管理系统集成路径）
