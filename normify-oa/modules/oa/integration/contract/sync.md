---
uid: 44e93dc2
id: oa.integration.contract.sync
parent: oa.integration.contract
state: planned
name: {zh: "台账双向同步", en: "Contract Ledger Sync"}
description:
  zh: >
      二期：合同管理系统作为独立模块接入，复用 OA 用户体系与审批引擎，通过 API 实现合同台账与审批流双向同步。
      
  en: >
      Phase two: the contract system joins as a standalone module reusing OA identity and the approval engine, syncing the contract ledger and approval flow both ways over APIs.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.695Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 523
    end_line: 523
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
