// 轻量 DDL 结构与一致性校验（无需数据库）
// 用法: node check-ddl.js <doc/data-model.md>
const fs = require('fs');

const file = process.argv[2];
const md = fs.readFileSync(file, 'utf8');

// 1. 抽取全部 sql 代码块
const blocks = [...md.matchAll(/```sql\r?\n([\s\S]*?)```/g)].map((m) => m[1]);
const sql = blocks.join('\n');
const findings = [];
const add = (sev, msg) => findings.push({ severity: sev, message: msg });

// 2. CREATE TABLE 解析
const tables = new Map();
const createRe = /CREATE TABLE\s+(\w+)\s*\(([\s\S]*?)\n\)\s*ENGINE=/g;
let m;
while ((m = createRe.exec(sql))) {
  const name = m[1];
  const body = m[2];
  if (tables.has(name)) add('error', `表重复定义: ${name}`);
  const cols = [];
  const pks = [];
  const fks = [];
  for (const rawLine of body.split(/\r?\n/)) {
    const line = rawLine.trim().replace(/,$/, '');
    if (!line || line.startsWith('--')) continue;
    let cm = line.match(/^(\w+)\s+([A-Za-z]+(?:\([^)]*\))?)/);
    if (cm && !/^(PRIMARY|UNIQUE|KEY|CONSTRAINT|FOREIGN|CHECK|INDEX)$/i.test(cm[1])) {
      cols.push({ name: cm[1], type: cm[2] });
      continue;
    }
    if (/^PRIMARY KEY/i.test(line)) pks.push(line);
    if (/^CONSTRAINT\s+\w+\s+FOREIGN KEY/i.test(line)) {
      const f = line.match(/FOREIGN KEY\s*\((\w+)\)\s*REFERENCES\s+(\w+)\s*\((\w+)\)/);
      if (f) fks.push({ col: f[1], refTable: f[2], refCol: f[3] });
      else add('error', `${name}: 无法解析外键定义 → ${line}`);
    }
  }
  if (!pks.length) add('error', `${name}: 缺少 PRIMARY KEY`);
  if (!cols.length) add('error', `${name}: 未解析到列定义`);
  tables.set(name, { cols, pks, fks });
}

// 3. 外键目标校验
for (const [name, t] of tables) {
  for (const f of t.fks) {
    if (!tables.has(f.refTable)) add('error', `${name}.${f.col} 引用不存在的表 ${f.refTable}`);
    else {
      const target = tables.get(f.refTable);
      if (!target.cols.some((c) => c.name === f.refCol) && !/^id$/.test(f.refCol)) {
        add('error', `${name}.${f.col} → ${f.refTable}.${f.refCol}（目标列不存在）`);
      }
      if (!t.cols.some((c) => c.name === f.col)) add('error', `${name}: 外键列 ${f.col} 未定义`);
    }
  }
}

// 4. 金额字段禁止浮点
for (const [name, t] of tables) {
  for (const c of t.cols) {
    if (/amount|money|price/i.test(c.name) && /FLOAT|DOUBLE|REAL/i.test(c.type)) {
      add('error', `${name}.${c.name}: 金额字段使用了浮点类型 ${c.type}`);
    }
  }
}

// 5. 文档中提到的表名是否都在 DDL 中（排除历史库 _history 与附录示例）
const mentioned = new Set([...md.matchAll(/`?(?:sys|flow|form)_[a-z_]+`?/g)].map((x) => x[0].replace(/`/g, '')));
for (const t of mentioned) {
  if (/_history$/.test(t)) continue;
  if (!tables.has(t) && !/^sys_(log|message|thread|role|user|org|permission|dict|login)/.test(t)) {
    add('warning', `文档提到 ${t}，但 DDL 中未找到该表`);
  }
}
// 反向：DDL 里的表是否都在第 11 章清单出现
const tableListStart = md.indexOf('## 11.');
const tableList = tableListStart >= 0 ? md.slice(tableListStart) : '';
for (const name of tables.keys()) {
  if (!tableList.includes(name)) add('warning', `表 ${name} 未出现在第 11 章清单中`);
}

// 6. 统计
const byDomain = {};
for (const [name] of tables) {
  const d = /^sys_org|^sys_user|^sys_login/.test(name) ? '身份与组织'
    : /^sys_role|^sys_permission|^sys_dict/.test(name) ? '权限'
    : /^flow_template|^flow_node$|^form_data/.test(name) ? '流程定义'
    : /^flow_instance|^flow_node_instance|^flow_task|^flow_routing|^flow_supplement/.test(name) ? '运行时'
    : '签名/附件/消息/审计';
  byDomain[d] = (byDomain[d] || 0) + 1;
}

const summary = { errors: 0, warnings: 0 };
for (const f of findings) summary[f.severity === 'error' ? 'errors' : 'warnings']++;
console.log(JSON.stringify({
  file,
  sqlBlocks: blocks.length,
  tables: tables.size,
  byDomain,
  checkConstraints: (sql.match(/CONSTRAINT\s+chk_\w+\s+CHECK/g) || []).length,
  foreignKeys: (sql.match(/FOREIGN KEY/g) || []).length,
  indexes: (sql.match(/\bKEY\s+\w+\s*\(/g) || []).length,
  triggers: (sql.match(/CREATE TRIGGER/g) || []).length,
  findings,
  summary,
}, null, 2));
process.exit(summary.errors ? 1 : 0);
