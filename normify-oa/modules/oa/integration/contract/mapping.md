---
uid: 4649e13b
id: oa.integration.contract.mapping
parent: oa.integration.contract
name: {zh: "字段映射", en: "Field Mapping"}
description:
  zh: >
      OA 合同审批数据与合同系统台账的字段对应关系，以配置方式维护，切换对接时不需要改代码。
      
  en: >
      Field mapping between OA contract approval data and the contract system ledger, kept as configuration so the handover does not require code changes.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.072Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.2 合同管理系统集成路径`（§8.2 合同管理系统集成路径）
