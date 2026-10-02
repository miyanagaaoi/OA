---
uid: 4649e13b
id: oa.integration.contract.mapping
parent: oa.integration.contract
state: planned
name: {zh: "字段映射", en: "Field Mapping"}
description:
  zh: >
      OA 合同审批数据与合同系统台账的字段对应关系，以配置方式维护，切换对接时不需要改代码。
      
  en: >
      Field mapping between OA contract approval data and the contract system ledger, kept as configuration so the handover does not require code changes.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.695Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 518
    end_line: 523
apis:
  - protocol: http
    method: GET
    path: "/api/v1/integration/contracts/mapping"
    description:
      zh: >
          查询字段映射配置。
          
      en: >
          Reads the field-mapping configuration.
          
  - protocol: file
    path: "config/contract-field-mapping.yml"
    description:
      zh: >
          OA 与合同系统字段映射。
          
      en: >
          Field mapping between OA and the contract system.
          
---
