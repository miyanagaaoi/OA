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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.744Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
