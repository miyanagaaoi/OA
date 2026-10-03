#!/usr/bin/env node
/**
 * tools/check-templates-sql.js
 * ---------------------------------------------------------------------------
 * 校验 oa-deploy/sql/03-templates.sql（四类单据的流程模板 + 表单模板初始化脚本）。
 *
 * 用法：
 *   node tools/check-templates-sql.js oa-deploy/sql/03-templates.sql
 *   node tools/check-templates-sql.js            # 省略参数时用上面的默认路径
 *
 * 输出：stdout 打印 JSON 报告（stats / errors / warnings）；
 *       存在 error 时进程退出码为 1，否则为 0（warning 不影响退出码）。
 *
 * 零第三方依赖（只用 Node 内置 fs / path）。
 *
 * 校验清单（对应 templates.md §1 / §2、forms.md §2–§5、enums.md §2/§10.2/§11、dict-seed.md §0.3）：
 *   1. flow_template 恰好 4 条（matter / fund / contract / seal），version = 1、node_count = 7；
 *   2. 每份 form_schema_json 能从 SQL 字面量中正确提取（含 '' 反转义）并 JSON.parse 通过；
 *      顶层键齐全、fields 非空、字段项必填键齐全、type 属 14 种枚举、
 *      optionsSource.dictType 属字典白名单、金额字段带 amountRange(max/scale)、
 *      sections 引用的字段 code 存在、事项单体现 involve_cost 对 amount / cost_bearer 的联动；
 *   3. flow_node 恰好 28 行值组；每个模板 7 个 seq（1..7）齐全且 node_code 与序号对应；
 *   4. ⑦ decision_mode = NULL；② 在 matter 有 skip_condition、在其余三个模板为 NULL；
 *   5. allow_route = 1 只出现在 finance_review / group_leader / chairman；allow_jump 全 0；
 *      ② timeout_hours = 48，其余 = 24。
 */
'use strict';

const fs = require('fs');
const path = require('path');

const DEFAULT_FILE = 'oa-deploy/sql/03-templates.sql';

// --- 硬编码的权威值域（enums.md §2 / §10.2 / §11、dict-seed.md §0.3） -------------

const TEMPLATE_CODES = ['matter', 'fund', 'contract', 'seal'];

const SEQ_NODE_CODE = {
  1: 'dept_leader',
  2: 'finance_review',
  3: 'branch_leader',
  4: 'subsidiary_gm',
  5: 'group_leader',
  6: 'chairman',
  7: 'archive_register',
};

/** enums.md §11：字段类型 14 种 */
const FIELD_TYPES = new Set([
  'text', 'textarea', 'number', 'amount', 'select', 'multiselect',
  'date', 'daterange', 'user', 'org', 'tag', 'boolean', 'file', 'files',
]);

/** dict-seed.md §0.3：字典类型白名单 */
const DICT_WHITELIST = new Set([
  'matter_category', 'contract_type', 'seal_type', 'cert_type',
  'payment_method', 'group_dept', 'review_dept_other', 'return_status',
]);

/** templates.md §2.2：字段项必填键 */
const FIELD_REQUIRED_KEYS = [
  'code', 'label', 'printLabel', 'printVisible', 'type',
  'required', 'rules', 'maxLength', 'readonlyAfterSubmit',
];

const AMOUNT_MAX = '99999999999.99';
const ROUTE_ALLOWED_CODES = new Set(['finance_review', 'group_leader', 'chairman']);

// --- SQL 解析 ---------------------------------------------------------------

/** 去掉 BOM */
function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

/** 跳过一段字符串字面量，返回结束位置（不含闭合引号之后） */
function skipString(text, start) {
  const q = text[start];
  let i = start + 1;
  while (i < text.length) {
    const c = text[i];
    if (c === '\\' && q !== '`') { i += 2; continue; }
    if (c === q) {
      if (text[i + 1] === q) { i += 2; continue; } // '' / "" 转义
      return i + 1;
    }
    i++;
  }
  return i;
}

/** 按顶层分号切分语句，剥离 -- / # / 注释块；字符串内的分号不切分 */
function splitStatements(sql) {
  const out = [];
  let buf = '';
  let i = 0;
  const n = sql.length;
  while (i < n) {
    const c = sql[i];
    if (c === "'" || c === '"' || c === '`') {
      const end = skipString(sql, i);
      buf += sql.slice(i, end);
      i = end;
      continue;
    }
    if (c === '-' && sql[i + 1] === '-' && (i + 2 >= n || /\s/.test(sql[i + 2]))) {
      while (i < n && sql[i] !== '\n') i++;
      continue;
    }
    if (c === '#') {
      while (i < n && sql[i] !== '\n') i++;
      continue;
    }
    if (c === '/' && sql[i + 1] === '*') {
      i += 2;
      while (i < n && !(sql[i] === '*' && sql[i + 1] === '/')) i++;
      i += 2;
      continue;
    }
    if (c === ';') {
      out.push(buf);
      buf = '';
      i++;
      continue;
    }
    buf += c;
    i++;
  }
  if (buf.trim()) out.push(buf);
  return out;
}

/** 按顶层逗号切分（忽略括号内与字符串内的逗号） */
function splitTopLevel(text) {
  const parts = [];
  let depth = 0;
  let buf = '';
  let i = 0;
  while (i < text.length) {
    const c = text[i];
    if (c === "'" || c === '"' || c === '`') {
      const end = skipString(text, i);
      buf += text.slice(i, end);
      i = end;
      continue;
    }
    if (c === '(') { depth++; buf += c; i++; continue; }
    if (c === ')') { depth--; buf += c; i++; continue; }
    if (c === ',' && depth === 0) { parts.push(buf); buf = ''; i++; continue; }
    buf += c;
    i++;
  }
  if (buf.trim() !== '') parts.push(buf);
  return parts;
}

/** 截断 INSERT 语句尾部的 ON DUPLICATE KEY UPDATE 子句（顶层扫描） */
function stripOnDuplicate(stmt) {
  let depth = 0;
  let i = 0;
  while (i < stmt.length) {
    const c = stmt[i];
    if (c === "'" || c === '"' || c === '`') { i = skipString(stmt, i); continue; }
    if (c === '(') { depth++; i++; continue; }
    if (c === ')') { depth--; i++; continue; }
    if (depth === 0 && /^ON\s+DUPLICATE\s+KEY\s+UPDATE\b/i.test(stmt.slice(i))) {
      return stmt.slice(0, i);
    }
    i++;
  }
  return stmt;
}

/** 解析 INSERT INTO tbl (cols) VALUES (...),(...) */
function parseInsert(stmt) {
  const m = /^\s*INSERT\s+(?:IGNORE\s+)?INTO\s+`?([A-Za-z0-9_]+)`?\s*\(/i.exec(stmt);
  if (!m) return null;
  const table = m[1];
  const openIdx = m[0].length - 1;
  let depth = 0;
  let j = openIdx;
  for (; j < stmt.length; j++) {
    const c = stmt[j];
    if (c === '(') depth++;
    else if (c === ')') { depth--; if (depth === 0) break; }
  }
  const cols = stmt.slice(openIdx + 1, j).split(',').map((s) => s.trim().replace(/^`|`$/g, ''));
  const rest = stmt.slice(j + 1);
  const vm = /^\s*VALUES\s*/i.exec(rest);
  if (!vm) return null;
  return { table, cols, valuesText: stripOnDuplicate(rest.slice(vm[0].length)) };
}

/** 把 VALUES 子句切成值组（每组为字符串数组） */
function parseTuples(valuesText) {
  const groups = [];
  for (const raw of splitTopLevel(valuesText)) {
    const t = raw.trim();
    if (!t) continue;
    if (!(t.startsWith('(') && t.endsWith(')'))) {
      groups.push({ malformed: t, values: [] });
      continue;
    }
    const inner = t.slice(1, -1);
    groups.push({ values: splitTopLevel(inner).map((s) => s.trim()) });
  }
  return groups;
}

/** 还原 SQL 字符串字面量（'' 反转义、反斜杠转义） */
function unescapeSqlString(token) {
  const q = token[0];
  let body = token.slice(1);
  if (body.endsWith(q)) body = body.slice(0, -1);
  let out = '';
  for (let i = 0; i < body.length; i++) {
    const c = body[i];
    if (c === '\\' && q === "'") {
      const nx = body[i + 1];
      const map = { n: '\n', t: '\t', r: '\r', 0: '\0', b: '\b', Z: '\u001a', '\\': '\\', "'": "'", '"': '"' };
      if (Object.prototype.hasOwnProperty.call(map, nx)) { out += map[nx]; i++; continue; }
      out += c;
      continue;
    }
    if (c === q && body[i + 1] === q) { out += q; i++; continue; }
    out += c;
  }
  return out;
}

/** 值 token → { kind, value, raw } */
function parseValue(token) {
  const t = (token || '').trim();
  if (/^NULL$/i.test(t)) return { kind: 'null', value: null, raw: t };
  if (t.startsWith("'") || t.startsWith('"')) {
    return { kind: 'string', value: unescapeSqlString(t), raw: t };
  }
  if (/^-?\d+(?:\.\d+)?$/.test(t)) return { kind: 'number', value: Number(t), raw: t };
  return { kind: 'raw', value: t, raw: t };
}

// --- 主流程 -----------------------------------------------------------------

function main() {
  const target = process.argv[2] || DEFAULT_FILE;
  const abs = path.resolve(process.cwd(), target);

  const errors = [];
  const warnings = [];
  const err = (code, message, context) => errors.push({ code, message, context });
  const warn = (code, message, context) => warnings.push({ code, message, context });

  if (!fs.existsSync(abs)) {
    const report = {
      ok: false, file: target,
      stats: { templates: 0, nodes: 0, jsonParsed: 0, jsonTotal: 0 },
      errors: [{ code: 'FILE_NOT_FOUND', message: `找不到文件：${abs}` }],
      warnings: [],
    };
    process.stdout.write(JSON.stringify(report, null, 2) + '\n');
    process.exitCode = 1;
    return;
  }

  const sql = stripBom(fs.readFileSync(abs, 'utf8'));
  const statements = splitStatements(sql).filter((s) => /^\s*INSERT\s/i.test(s));

  const templateRows = [];
  const nodeRows = [];

  for (const stmt of statements) {
    const ins = parseInsert(stmt);
    if (!ins) {
      err('INSERT_PARSE', '存在无法解析的 INSERT 语句（列清单或 VALUES 子句缺失）', {
        head: stmt.trim().slice(0, 120),
      });
      continue;
    }
    const tuples = parseTuples(ins.valuesText);
    for (const g of tuples) {
      if (g.malformed) {
        err('TUPLE_PARSE', '存在无法解析的值组（缺少外层括号）', { head: g.malformed.slice(0, 120) });
        continue;
      }
      if (g.values.length !== ins.cols.length) {
        err('TUPLE_ARITY', `${ins.table} 存在值组个数与列清单不匹配：列 ${ins.cols.length} 个，值 ${g.values.length} 个`, {
          table: ins.table,
          columns: ins.cols.length,
          values: g.values.length,
        });
        continue;
      }
      const obj = {};
      ins.cols.forEach((col, idx) => { obj[col] = parseValue(g.values[idx]); });
      if (ins.table.toLowerCase() === 'flow_template') templateRows.push(obj);
      else if (ins.table.toLowerCase() === 'flow_node') nodeRows.push(obj);
    }
  }

  // ---------------- 1. flow_template ----------------
  if (templateRows.length !== 4) {
    err('TEMPLATE_COUNT', `flow_template 应为 4 行，实际 ${templateRows.length} 行`,
      { actual: templateRows.length });
  }

  const byCode = new Map();
  for (const row of templateRows) {
    const code = row.code && row.code.kind === 'string' ? row.code.value : null;
    if (!code) {
      err('TEMPLATE_CODE', '存在 code 非字符串字面量的模板行');
      continue;
    }
    if (byCode.has(code)) err('TEMPLATE_DUP', `模板 code 重复：${code}`);
    byCode.set(code, row);
  }
  for (const code of TEMPLATE_CODES) {
    if (!byCode.has(code)) err('TEMPLATE_MISSING', `缺少模板 code=${code}`);
  }
  for (const code of byCode.keys()) {
    if (!TEMPLATE_CODES.includes(code)) err('TEMPLATE_UNKNOWN', `出现未登记的模板 code=${code}`);
  }

  let jsonParsed = 0;
  const jsonTotal = templateRows.length;
  const fieldCounts = {};
  const fieldCodes = {};
  const dictTypes = {};

  for (const code of TEMPLATE_CODES) {
    const row = byCode.get(code);
    if (!row) continue;
    const ctx = { template: code };

    const version = row.version && row.version.kind === 'number' ? row.version.value : null;
    if (version !== 1) err('TEMPLATE_VERSION', `${code}.version 应为 1，实际 ${row.version && row.version.raw}`, ctx);

    const nodeCount = row.node_count && row.node_count.kind === 'number' ? row.node_count.value : null;
    if (nodeCount !== 7) err('TEMPLATE_NODE_COUNT', `${code}.node_count 应为 7，实际 ${row.node_count && row.node_count.raw}`, ctx);

    const status = row.status && row.status.kind === 'string' ? row.status.value : null;
    if (status !== 'published') err('TEMPLATE_STATUS', `${code}.status 应为 published，实际 ${row.status && row.status.raw}`, ctx);

    if (!row.published_at || row.published_at.kind === 'null') {
      err('TEMPLATE_PUBLISHED_AT', `${code}.published_at 不得为空`, ctx);
    }

    // Q6 / Q7 模板级闸门配置（templates.md §1.7 的 5 个键；列定义见 data-model.md §4.1）
    // 种子必须写 V0.4 定稿默认值 —— 默认行为不变，且不允许再靠手写迁移补默认值。
    const GATE_DEFAULTS = [
      ['max_return_count', 5],
      ['max_supplement_count', 3],
      ['supplement_deadline_days', 3],
      ['supplement_deadline_type', 'working'],
      ['on_supplement_timeout', 'notify'],
    ];
    for (const [key, want] of GATE_DEFAULTS) {
      const token = row[key];
      if (!token) {
        err('TEMPLATE_GATE_MISSING',
          `${code}: flow_template 种子缺少闸门配置列 ${key}（templates.md §1.7 / data-model.md §4.1）`, ctx);
        continue;
      }
      const got = token.kind === 'string' || token.kind === 'number' ? token.value : token.raw;
      if (got !== want) {
        err('TEMPLATE_GATE_DEFAULT',
          `${code}.${key} 应为 V0.4 定稿默认值 ${JSON.stringify(want)}，实际 ${JSON.stringify(got)}`, ctx);
      }
    }

    const formType = row.form_type && row.form_type.kind === 'string' ? row.form_type.value : null;
    if (formType !== code) err('TEMPLATE_FORM_TYPE', `${code}.form_type 应为 ${code}，实际 ${formType}`, ctx);

    // form_schema_json
    const schemaToken = row.form_schema_json;
    if (!schemaToken || schemaToken.kind !== 'string') {
      err('SCHEMA_NOT_STRING', `${code}.form_schema_json 不是单引号字符串字面量`, ctx);
      continue;
    }
    let schema;
    try {
      schema = JSON.parse(schemaToken.value);
      jsonParsed++;
    } catch (e) {
      err('SCHEMA_JSON_PARSE', `${code}.form_schema_json 无法 JSON.parse：${e.message}`, ctx);
      continue;
    }

    if (schema.form_type !== code) err('SCHEMA_FORM_TYPE', `${code}: schema.form_type 应为 ${code}`, ctx);
    if (schema.template_code !== code) err('SCHEMA_TEMPLATE_CODE', `${code}: schema.template_code 应为 ${code}`, ctx);
    if (schema.schema_version !== version) {
      err('SCHEMA_VERSION', `${code}: schema.schema_version(${schema.schema_version}) 必须等于 flow_template.version(${version})`, ctx);
    }
    if (!Array.isArray(schema.fields) || schema.fields.length === 0) {
      err('SCHEMA_FIELDS_EMPTY', `${code}: schema.fields 必须为非空数组`, ctx);
      continue;
    }
    fieldCounts[code] = schema.fields.length;
    fieldCodes[code] = schema.fields.map((f) => f.code);
    dictTypes[code] = schema.fields
      .filter((f) => f.optionsSource && f.optionsSource.dictType)
      .map((f) => `${f.code}->${f.optionsSource.dictType}`);

    const codes = new Set();
    const amountFields = [];
    for (const f of schema.fields) {
      const fctx = { template: code, field: f && f.code };
      for (const k of FIELD_REQUIRED_KEYS) {
        if (!(k in f)) err('FIELD_KEY_MISSING', `${code}.${f && f.code}: 字段项缺少必填键 ${k}`, fctx);
      }
      if (typeof f.code !== 'string' || !/^[a-z][a-z0-9_]*$/.test(f.code)) {
        err('FIELD_CODE', `${code}: 字段 code 非法：${JSON.stringify(f.code)}`, fctx);
      }
      if (codes.has(f.code)) err('FIELD_DUP', `${code}: 字段 code 重复：${f.code}`, fctx);
      codes.add(f.code);
      if (!FIELD_TYPES.has(f.type)) {
        err('FIELD_TYPE', `${code}.${f.code}: type=${JSON.stringify(f.type)} 不在 14 种字段类型内`, fctx);
      }
      if (!Array.isArray(f.rules)) {
        err('FIELD_RULES', `${code}.${f.code}: rules 必须是数组（无条件必填时也不得为 null）`, fctx);
      }
      if (f.optionsSource !== undefined) {
        const dt = f.optionsSource && f.optionsSource.dictType;
        if (!DICT_WHITELIST.has(dt)) {
          err('FIELD_DICT', `${code}.${f.code}: optionsSource.dictType=${JSON.stringify(dt)} 不在字典白名单内`, fctx);
        }
        if (f.optionsSource.kind !== 'dict') {
          warn('FIELD_DICT_KIND', `${code}.${f.code}: optionsSource.kind 建议为 dict`, fctx);
        }
      }
      if (f.type === 'amount') {
        amountFields.push(f.code);
        const range = (f.rules || []).find((r) => r && r.type === 'amountRange');
        if (!range) {
          err('AMOUNT_RANGE', `${code}.${f.code}: amount 字段缺少 amountRange 规则`, fctx);
        } else {
          const max = String(range.max);
          if (max !== AMOUNT_MAX) {
            err('AMOUNT_MAX', `${code}.${f.code}: amountRange.max 应为 ${AMOUNT_MAX}，实际 ${max}`, fctx);
          }
          if (range.scale !== 2) {
            err('AMOUNT_SCALE', `${code}.${f.code}: amountRange.scale 应为 2，实际 ${JSON.stringify(range.scale)}`, fctx);
          }
        }
      }
    }

    // sections 引用完整性
    if (Array.isArray(schema.sections)) {
      for (const sec of schema.sections) {
        if (!sec || !Array.isArray(sec.fields)) continue;
        for (const ref of sec.fields) {
          if (!codes.has(ref)) {
            err('SECTION_REF', `${code}: section ${sec.id} 引用了不存在的字段 code=${ref}`, ctx);
          }
        }
      }
      const sectioned = new Set(schema.sections.flatMap((s) => (s && Array.isArray(s.fields) ? s.fields : [])));
      for (const f of schema.fields) {
        if (!sectioned.has(f.code)) warn('SECTION_MISSING', `${code}: 字段 ${f.code} 未被任何 section 引用`, ctx);
      }
    } else {
      warn('SCHEMA_SECTIONS', `${code}: 未定义 sections（缺省时按 fields 顺序单段渲染）`, ctx);
    }

    // 事项单：involve_cost 对 amount / cost_bearer 的联动
    if (code === 'matter') {
      const byField = new Map(schema.fields.map((f) => [f.code, f]));
      if (!byField.has('involve_cost')) {
        err('MATTER_INVOLVE_COST', 'matter: 缺少 involve_cost 字段', ctx);
      } else if (byField.get('involve_cost').type !== 'boolean') {
        err('MATTER_INVOLVE_COST_TYPE', 'matter.involve_cost: 类型应为 boolean', ctx);
      }
      for (const dep of ['amount', 'cost_bearer']) {
        const f = byField.get(dep);
        if (!f) { err('MATTER_LINKAGE_FIELD', `matter: 缺少 ${dep} 字段`, ctx); continue; }
        const hasCond = (f.rules || []).some((r) => r && r.type === 'conditionalRequired'
          && r.when && r.when.field === 'involve_cost');
        const lk = f.linkage || {};
        const linked = ['visibleWhen', 'requiredWhen', 'clearWhen'].every(
          (k) => lk[k] && lk[k].field === 'involve_cost'
        );
        if (!hasCond) err('MATTER_LINKAGE_RULE', `matter.${dep}: 缺少 involve_cost 条件的 conditionalRequired 规则`, ctx);
        if (!linked) err('MATTER_LINKAGE', `matter.${dep}: linkage 未体现对 involve_cost 的联动`, ctx);
      }
    }
  }

  // ---------------- 2. flow_node ----------------
  const NODE_COLUMNS = [
    'template_id', 'seq', 'node_code', 'name', 'node_type', 'approver_rule',
    'approver_param', 'decision_mode', 'pass_threshold', 'sign_policy',
    'timeout_hours', 'timeout_cc_superior', 'allow_add_sign', 'allow_jump',
    'allow_route', 'skip_condition',
  ];

  if (nodeRows.length !== 28) {
    err('NODE_COUNT', `flow_node 应为 28 行值组（4 × 7），实际 ${nodeRows.length} 行`, { actual: nodeRows.length });
  }

  const nodesByTemplate = new Map();
  for (const row of nodeRows) {
    const missing = NODE_COLUMNS.filter((c) => !(c in row));
    if (missing.length) {
      err('NODE_COLUMNS', `flow_node 值组缺少列：${missing.join(', ')}`);
      continue;
    }
    const sub = row.template_id.raw || '';
    const cm = /code\s*=\s*'([^']+)'/.exec(sub);
    const vm = /version\s*=\s*(\d+)/.exec(sub);
    if (!cm) {
      err('NODE_TEMPLATE_ID', `flow_node.template_id 不是形如 (SELECT id FROM flow_template WHERE code=... AND version=1) 的子查询：${sub.slice(0, 80)}`);
      continue;
    }
    const code = cm[1];
    const version = vm ? Number(vm[1]) : null;
    if (version !== 1) {
      err('NODE_TEMPLATE_VERSION', `flow_node 子查询的 version 应为 1（code=${code}）`, { template: code });
    }
    if (!nodesByTemplate.has(code)) nodesByTemplate.set(code, []);
    nodesByTemplate.get(code).push(row);
  }

  for (const code of TEMPLATE_CODES) {
    const rows = nodesByTemplate.get(code) || [];
    if (rows.length !== 7) {
      err('NODE_COUNT_PER_TEMPLATE', `${code}: 应为 7 个节点，实际 ${rows.length} 个`, { template: code });
    }
    const seqs = rows.map((r) => (r.seq.kind === 'number' ? r.seq.value : null)).sort((a, b) => a - b);
    const expect = [1, 2, 3, 4, 5, 6, 7];
    if (JSON.stringify(seqs) !== JSON.stringify(expect)) {
      err('NODE_SEQ', `${code}: seq 必须为 1..7 且不重复，实际 ${JSON.stringify(seqs)}`, { template: code });
    }
    for (const row of rows) {
      const seq = row.seq.kind === 'number' ? row.seq.value : null;
      const ctx = { template: code, seq };
      const nodeCode = row.node_code.kind === 'string' ? row.node_code.value : null;
      if (SEQ_NODE_CODE[seq] && nodeCode !== SEQ_NODE_CODE[seq]) {
        err('NODE_CODE_SEQ', `${code} seq=${seq}: node_code 应为 ${SEQ_NODE_CODE[seq]}，实际 ${nodeCode}`, ctx);
      }
      const isArchive = seq === 7;
      const expectedType = isArchive ? 'archive' : 'approve';
      const nodeType = row.node_type.kind === 'string' ? row.node_type.value : null;
      if (nodeType !== expectedType) {
        err('NODE_TYPE', `${code} seq=${seq}: node_type 应为 ${expectedType}，实际 ${nodeType}`, ctx);
      }

      // decision_mode / pass_threshold
      if (isArchive) {
        if (row.decision_mode.kind !== 'null') {
          err('NODE_ARCHIVE_DECISION', `${code} seq=7: decision_mode 必须为 NULL，实际 ${row.decision_mode.raw}`, ctx);
        }
        if (row.pass_threshold.kind !== 'null') {
          err('NODE_ARCHIVE_THRESHOLD', `${code} seq=7: pass_threshold 必须为 NULL，实际 ${row.pass_threshold.raw}`, ctx);
        }
        if (!/"role_code"\s*:\s*"admin"/.test(row.approver_param.raw || '')) {
          err('NODE_ARCHIVE_PARAM',
            `${code} seq=7: approver_param 应为 {"role_code":"admin"}（一期由系统管理员承担归档登记；`
            + 'sys_role 恰为 REQ-ADMIN-003 的 9 个内置角色，无 finance_clerk / 档案管理员，见 templates.md §1.1）', ctx);
        }
      } else {
        const dm = row.decision_mode.kind === 'string' ? row.decision_mode.value : null;
        if (dm !== 'any') err('NODE_DECISION', `${code} seq=${seq}: decision_mode 应为 any，实际 ${dm}`, ctx);
        if (row.pass_threshold.kind !== 'null') {
          err('NODE_THRESHOLD', `${code} seq=${seq}: pass_threshold 应为 NULL（缺省过半），实际 ${row.pass_threshold.raw}`, ctx);
        }
      }

      // sign_policy：⑤⑥ = required；⑦ = none；其余 = optional
      const signPolicy = row.sign_policy.kind === 'string' ? row.sign_policy.value : null;
      const expectSign = isArchive ? 'none' : (seq === 5 || seq === 6 ? 'required' : 'optional');
      if (signPolicy !== expectSign) {
        err('NODE_SIGN_POLICY', `${code} seq=${seq}: sign_policy 应为 ${expectSign}，实际 ${signPolicy}`, ctx);
      }

      // timeout_hours：② = 48，其余 = 24
      const timeout = row.timeout_hours.kind === 'number' ? row.timeout_hours.value : null;
      const expectTimeout = seq === 2 ? 48 : 24;
      if (timeout !== expectTimeout) {
        err('NODE_TIMEOUT', `${code} seq=${seq}: timeout_hours 应为 ${expectTimeout}，实际 ${timeout}`, ctx);
      }

      // timeout_cc_superior：§1 未指明 → 全为 0
      const cc = row.timeout_cc_superior.kind === 'number' ? row.timeout_cc_superior.value : null;
      if (cc !== 0) {
        warn('NODE_TIMEOUT_CC', `${code} seq=${seq}: timeout_cc_superior 基线取 0，实际 ${row.timeout_cc_superior.raw}`, ctx);
      }

      // allow_add_sign：①–⑥ = 1，⑦ = 0
      const addSign = row.allow_add_sign.kind === 'number' ? row.allow_add_sign.value : null;
      const expectAddSign = isArchive ? 0 : 1;
      if (addSign !== expectAddSign) {
        err('NODE_ADD_SIGN', `${code} seq=${seq}: allow_add_sign 应为 ${expectAddSign}，实际 ${addSign}`, ctx);
      }

      // allow_jump：全部 0
      const jump = row.allow_jump.kind === 'number' ? row.allow_jump.value : null;
      if (jump !== 0) {
        err('NODE_ALLOW_JUMP', `${code} seq=${seq}: allow_jump 必须为 0，实际 ${row.allow_jump.raw}`, ctx);
      }

      // allow_route：仅 finance_review / group_leader / chairman = 1
      const route = row.allow_route.kind === 'number' ? row.allow_route.value : null;
      const expectRoute = ROUTE_ALLOWED_CODES.has(nodeCode) ? 1 : 0;
      if (route !== expectRoute) {
        err('NODE_ALLOW_ROUTE', `${code} seq=${seq}: allow_route 应为 ${expectRoute}（仅 finance_review/group_leader/chairman 为 1），实际 ${route}`, ctx);
      }

      // skip_condition：仅 matter 的 ② 有值
      if (seq === 2) {
        if (code === 'matter') {
          if (row.skip_condition.kind !== 'string') {
            err('NODE_SKIP_MATTER', 'matter seq=2: 必须有 skip_condition 字符串', ctx);
          } else {
            let cond = null;
            try { cond = JSON.parse(row.skip_condition.value); } catch (e) {
              err('NODE_SKIP_JSON', `matter seq=2: skip_condition 不是合法 JSON：${e.message}`, ctx);
            }
            if (cond) {
              if (cond.field !== 'involve_cost' || cond.op !== 'eq' || cond.value !== false) {
                err('NODE_SKIP_SHAPE', `matter seq=2: skip_condition 应为 {"field":"involve_cost","op":"eq","value":false}，实际 ${row.skip_condition.value}`, ctx);
              }
            }
          }
        } else if (row.skip_condition.kind !== 'null') {
          err('NODE_SKIP_OTHER', `${code} seq=2: 三类单据恒为「涉及」，skip_condition 必须为 NULL，实际 ${row.skip_condition.raw}`, ctx);
        }
      } else if (row.skip_condition.kind !== 'null') {
        warn('NODE_SKIP_UNEXPECTED', `${code} seq=${seq}: 非 ② 节点出现 skip_condition=${row.skip_condition.raw}`, ctx);
      }
    }
  }

  const nodesPerTemplate = {};
  for (const code of TEMPLATE_CODES) nodesPerTemplate[code] = (nodesByTemplate.get(code) || []).length;

  const report = {
    ok: errors.length === 0,
    file: target,
    stats: {
      templates: templateRows.length,
      templateCodes: [...byCode.keys()],
      nodes: nodeRows.length,
      nodesPerTemplate,
      nodeStatements: statements.filter((s) => /INTO\s+`?flow_node`?\s*\(/i.test(s)).length,
      jsonTotal,
      jsonParsed,
      fieldCounts,
      fieldCodes,
      dictTypes,
    },
    errors,
    warnings,
  };

  process.stdout.write(JSON.stringify(report, null, 2) + '\n');
  process.exitCode = errors.length > 0 ? 1 : 0;
}

main();
