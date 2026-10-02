#!/usr/bin/env node
/**
 * 初始化 SQL 生成器（可复现：文档 → 可执行 SQL）
 *
 * 用法:
 *   node tools/gen-init-sql.js            生成 oa-deploy/sql/01-schema.sql 与 02-dict-seed.sql
 *   node tools/gen-init-sql.js --check    只校验、不写文件
 *
 * 设计原则：**文档是唯一真源**。本脚本把 doc/data-model.md 与 doc/dict-seed.md 中的
 * ```sql 代码块按文档顺序原样抽取并拼接，不做任何改写，保证 SQL 与评审过的文档逐字一致。
 * 修改表结构请改文档，然后重跑本脚本。
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const OUT_DIR = path.join(ROOT, 'oa-deploy', 'sql');
const CHECK_ONLY = process.argv.includes('--check');

const DICT_WHITELIST = [
  'matter_category',
  'contract_type',
  'seal_type',
  'cert_type',
  'payment_method',
  'group_dept',
  'review_dept_other',
  'return_status',
];

/** 抽取 markdown 中的 sql 代码块（保持文档顺序） */
function extractSqlBlocks(mdPath) {
  const md = fs.readFileSync(mdPath, 'utf8');
  return [...md.matchAll(/```sql\r?\n([\s\S]*?)```/g)].map((m) => m[1].trim());
}

function banner(title, sources, notes) {
  const stamp = new Date().toISOString();
  const lines = [
    '-- ============================================================================',
    `-- ${title}`,
    '-- ----------------------------------------------------------------------------',
    `-- 生成时间: ${stamp}`,
    '-- 生成工具: tools/gen-init-sql.js（请勿手工编辑本文件，改文档后重跑）',
    ...sources.map((s) => `-- 真源文档: ${s}`),
    '--',
    ...notes.map((n) => `-- ${n}`),
    '-- ============================================================================',
    '',
    'SET NAMES utf8mb4;',
    '',
  ];
  return lines.join('\n');
}

/** 表结构自检：解析 CREATE TABLE，检查主键 / 重复列 / 外键目标 / 金额浮点 */
function analyzeSchema(sql) {
  const errors = [];
  const warnings = [];
  const tables = new Map();

  const createRe = /CREATE TABLE\s+(\w+)\s*\(([\s\S]*?)\n\)\s*ENGINE=/g;
  let m;
  while ((m = createRe.exec(sql))) {
    const name = m[1];
    const body = m[2];
    if (tables.has(name)) errors.push(`表重复定义: ${name}`);
    const cols = [];
    let pk = false;
    for (const raw of body.split(/\r?\n/)) {
      const line = raw.trim().replace(/,$/, '');
      if (!line || line.startsWith('--')) continue;
      if (/^PRIMARY KEY/i.test(line)) { pk = true; continue; }
      const cm = line.match(/^(\w+)\s+([A-Za-z]+(?:\([^)]*\))?)/);
      if (cm && !/^(PRIMARY|UNIQUE|KEY|CONSTRAINT|FOREIGN|CHECK|INDEX)$/i.test(cm[1])) {
        cols.push({ name: cm[1], type: cm[2] });
      }
    }
    if (!pk) errors.push(`${name}: 缺少 PRIMARY KEY`);
    if (!cols.length) errors.push(`${name}: 未解析到列定义`);
    const seen = new Set();
    for (const c of cols) {
      if (seen.has(c.name)) errors.push(`${name}: 列重复定义 ${c.name}`);
      seen.add(c.name);
      if (/amount|money|price/i.test(c.name) && /FLOAT|DOUBLE|REAL/i.test(c.type)) {
        errors.push(`${name}.${c.name}: 金额字段使用浮点类型 ${c.type}`);
      }
    }
    tables.set(name, cols);
  }

  // 外键目标存在性
  const fkRe = /CONSTRAINT\s+\w+\s+FOREIGN KEY\s*\((\w+)\)\s*REFERENCES\s+(\w+)/g;
  let f;
  while ((f = fkRe.exec(sql))) {
    if (!tables.has(f[2])) errors.push(`外键引用了不存在的表: ${f[2]}`);
  }

  const triggers = (sql.match(/CREATE TRIGGER/gi) || []).length;
  const checks = (sql.match(/CONSTRAINT\s+chk_\w+\s+CHECK/gi) || []).length;
  const indexes = (sql.match(/\bKEY\s+\w+\s*\(/g) || []).length;
  if (triggers < 4) warnings.push(`触发器数量偏少（${triggers}），不可篡改至少应覆盖 sys_log / flow_signature / sys_thread`);

  return { tables, errors, warnings, triggers, checks, indexes };
}

/** 字典种子自检：幂等 INSERT 覆盖的 dict_type 是否都在白名单内、是否误建布尔字段的字典项 */
function analyzeDictSeed(sql, schemaSqlForDuplicateCheck) {
  const errors = [];
  const warnings = [];
  const pairs = new Map();
  const dictTypes = new Set();

  const tupleRe = /\(\s*'([a-z_]+)'\s*,\s*'([a-z0-9_]+)'\s*,/g;
  let t;
  while ((t = tupleRe.exec(sql))) {
    const dictType = t[1];
    const itemCode = t[2];
    dictTypes.add(dictType);
    const key = `${dictType}.${itemCode}`;
    if (pairs.has(key)) errors.push(`字典项重复: ${key}`);
    pairs.set(key, true);
  }

  for (const dt of dictTypes) {
    if (!DICT_WHITELIST.includes(dt)) errors.push(`dict_type 不在白名单内: ${dt}`);
  }
  for (const forbidden of ['payment_belong', 'planned_category']) {
    if (dictTypes.has(forbidden)) errors.push(`${forbidden} 是 checkbox 布尔字段，不得建字典项`);
  }
  // 漂移断言（2026-10-02 实战教训）：建表语句若已含某列，种子脚本里再 ADD COLUMN 同名列表会导致
  // Flyway 在**全新库**上执行 V1 → V2 时报 1060 Duplicate column name，应用直接起不来。
  for (const col of ['item_name_en', 'remark']) {
    const addRe = new RegExp(`ADD\\s+COLUMN\\s+${col}\\b`, 'i');
    if (addRe.test(sql) && new RegExp(`\\b${col}\\b`).test(schemaSqlForDuplicateCheck)) {
      errors.push(
        `字典种子重复添加列 ${col}：该列已由 01-schema.sql 的建表语句创建，` +
          '全新库上 Flyway V1→V2 会报 1060 Duplicate column name（应用无法启动）',
      );
    }
  }
  if (!/ON DUPLICATE KEY UPDATE/i.test(sql)) {
    warnings.push('未发现 ON DUPLICATE KEY UPDATE，脚本可能不是幂等的');
  }
  return { errors, warnings, dictTypes: [...dictTypes].sort(), rows: pairs.size };
}

function main() {
  const schemaSrc = path.join(ROOT, 'doc', 'data-model.md');
  const dictSrc = path.join(ROOT, 'doc', 'dict-seed.md');
  const report = { ok: true, files: [], errors: [], warnings: [] };

  // ---------- 01-schema.sql ----------
  const schemaBlocks = extractSqlBlocks(schemaSrc);
  const schemaSql = schemaBlocks.join('\n\n');
  const schema = analyzeSchema(schemaSql);
  report.errors.push(...schema.errors);
  report.warnings.push(...schema.warnings.map((w) => `[schema] ${w}`));

  const schemaOut = path.join(OUT_DIR, '01-schema.sql');
  const schemaContent =
    banner(
      '集团OA审批系统 · 01 表结构（27 张表 + 不可篡改触发器）',
      ['doc/data-model.md'],
      [
        '执行顺序：按文档顺序执行（身份与组织 → 权限 → 流程定义 → 运行时 → 签名/附件/消息/审计 → 表单数据）。',
        `包含：建表 ${schema.tables.size} 张、索引 ${schema.indexes} 个、CHECK ${schema.checks} 个、外键若干、不可篡改触发器 ${schema.triggers} 个。`,
        '不可篡改：sys_log 与 flow_signature 由数据库触发器拒绝 UPDATE 与 DELETE（AC-20）；',
        '          sys_thread（审批轨迹）一期由应用层只追加约束 + 审计校验保证（见 doc/data-model.md 8.1）。',
        '注意：触发器使用 mysql 客户端语法 DELIMITER //（Flyway 不识别，接入时需拆分为独立迁移或在启动时用 JDBC 创建）。',
        '字符集：utf8mb4；引擎：InnoDB。',
      ],
    ) + '\n' + schemaSql + '\n';

  // ---------- 02-dict-seed.sql ----------
  const dictBlocks = extractSqlBlocks(dictSrc);
  const dictSql = dictBlocks.join('\n\n');
  const dict = analyzeDictSeed(dictSql, schemaSql);
  report.errors.push(...dict.errors);
  report.warnings.push(...dict.warnings.map((w) => `[dict-seed] ${w}`));

  const dictOut = path.join(OUT_DIR, '02-dict-seed.sql');
  const dictContent =
    banner(
      '集团OA审批系统 · 02 数据字典种子（8 个 dict_type）',
      ['doc/dict-seed.md'],
      [
        '执行顺序：在 01-schema.sql 之后执行；可重复执行（幂等）。',
        '覆盖：' + dict.dictTypes.join(' / ') + `，共 ${dict.rows} 项。`,
        '注意：payment_belong 与 planned_category 是 checkbox 布尔字段，**不建字典项**（脚本已校验）。',
      ],
    ) + '\n' + dictSql + '\n';

  for (const [file, content] of [[schemaOut, schemaContent], [dictOut, dictContent]]) {
    report.files.push({
      path: path.relative(ROOT, file).replace(/\\/g, '/'),
      bytes: Buffer.byteLength(content, 'utf8'),
      lines: content.split(/\r?\n/).length,
    });
  }

  report.schema = {
    tables: schema.tables.size,
    triggers: schema.triggers,
    checks: schema.checks,
    indexes: schema.indexes,
  };
  report.dict = { dictTypes: dict.dictTypes.length, rows: dict.rows };
  report.ok = report.errors.length === 0;

  if (!CHECK_ONLY && report.ok) {
    fs.mkdirSync(OUT_DIR, { recursive: true });
    fs.writeFileSync(schemaOut, schemaContent, 'utf8');
    fs.writeFileSync(dictOut, dictContent, 'utf8');
  }

  console.log(JSON.stringify(report, null, 2));
  process.exit(report.ok ? 0 : 1);
}

main();
