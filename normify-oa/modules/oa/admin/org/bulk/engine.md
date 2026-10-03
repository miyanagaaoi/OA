---
uid: b6e17a35
id: oa.admin.org.bulk.engine
parent: oa.admin.org.bulk
name: {zh: "批量导入导出引擎", en: "Bulk Import/Export Engine"}
description:
  zh: >
      五类模板共用的导入导出引擎：CSV 解析与 RFC4180 转义、全量校验报告（行号 + 列 + 错误码 + 修正建议）、**错误零落库的单事务**与按业务键幂等 upsert、导入互斥（oa:import:* 分布式锁），以及**逐行数据域 fail-closed 闸门**（E-ORG/USER/LEAD/POS/ROLE-020：越域行即整批拒绝，不做静默跳过）。
      
  en: >
      The shared engine behind all five templates: CSV parsing with RFC4180 escaping, a full validation report (line, column, error code, fix suggestion), a single transaction with zero rows written on error, idempotent upsert by business key, an import mutex (oa:import:* lock) and the per-row fail-closed data-scope gate (E-ORG/USER/LEAD/POS/ROLE-020: one out-of-scope row rejects the whole batch instead of being silently skipped).
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.200Z"
fingerprint: 00c5ccf651c6912c3604e7db2fbaf4689b8d576f5f81f7f2f069f3ab8b7144d0
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/BulkImportService.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/CsvTable.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/ImportReport.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/ImportScopeGuard.java"
apis:
  - protocol: rpc
    path: "bulk.pipeline.preview"
    description:
      zh: >
          全量校验（dry-run）：解析 + 逐行规则 + 数据域闸门，返回逐行发现与整批判定。
          
      en: >
          Full validation (dry-run): parse, per-row rules and the data-scope gate, returning findings and the batch verdict.
          
  - protocol: rpc
    path: "bulk.pipeline.commit"
    description:
      zh: >
          确认执行：单事务幂等 upsert；存在 error 时整批拒绝、错误零落库。
          
      en: >
          Commit: single-transaction idempotent upsert; any error rejects the whole batch with zero rows written.
          
  - protocol: rpc
    path: "bulk.pipeline.lock"
    description:
      zh: >
          导入互斥锁（同一时刻只允许一个导入任务，占用冲突返回 40905）。
          
      en: >
          Import mutex: only one import at a time; contention yields 40905.
          
  - protocol: rpc
    path: "bulk.pipeline.assertRowInScope"
    description:
      zh: >
          逐行数据域断言：越域即追加 E-XXX-020 并判定整批失败。
          
      en: >
          Per-row data-scope assertion: out-of-scope rows raise E-XXX-020 and fail the batch.
          
deps:
  - kind: call
    to: oa.admin.org.bulk.import
    from_api: "rpc:bulk.pipeline.preview"
    to_api: "GET /api/v1/admin/bulk-import/kinds"
    label: {zh: "按导入类型取模板与列定义", en: "Resolve template by kind"}
---
