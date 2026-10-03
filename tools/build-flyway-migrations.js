#!/usr/bin/env node
/**
 * Flyway 迁移生成器：把 oa-deploy/sql 下的「交付脚本」转成 oa-server 的迁移文件。
 *
 * 用法:
 *   node tools/build-flyway-migrations.js           生成迁移文件
 *   node tools/build-flyway-migrations.js --check   校验现有迁移与交付脚本是否一致（CI 用）
 *
 * 为什么需要它：
 *   01-schema.sql 末尾的不可篡改触发器使用了 **mysql 客户端语法 `DELIMITER //`**，
 *   而 Flyway 的 MySQL 解析器不识别 `DELIMITER`。因此这里把触发器段**整段拆出**，
 *   写成 `db/trigger/immutable-triggers.sql`（语句以 `//` 分隔、不含 DELIMITER），
 *   由 `ImmutableTriggerInitializer` 在应用启动时幂等创建。
 *
 * 确定性（重要）：产物头部**不含墙钟时间戳**，只有确定性的溯源行（生成器自身 sha256 + 每个来源
 * 交付脚本的内容 sha256）。因此「同一输入 → 逐字节相同」，重跑生成器是幂等的：V1..V4 的
 * Flyway checksum 稳定，`validate-on-migrate: true` 下不会因重跑生成器而报 Migration checksum
 * mismatch（历史上头部的时间戳每次重跑都变，导致校验和漂移、必须重置库才能启动）。
 */
'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const SQL_DIR = path.join(ROOT, 'oa-deploy', 'sql');
const MIGRATION_DIR = path.join(ROOT, 'oa-server', 'src', 'main', 'resources', 'db', 'migration');
const TRIGGER_DIR = path.join(ROOT, 'oa-server', 'src', 'main', 'resources', 'db', 'trigger');
const CHECK = process.argv.includes('--check');
const GEN_REL = 'tools/build-flyway-migrations.js';

const read = (p) => fs.readFileSync(p, 'utf8').replace(/\r\n/g, '\n');

/** 内容 sha256 前 12 位（确定性溯源指纹；不参与任何密钥用途） */
function sha12(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex').slice(0, 12);
}

/** 生成器自身的 sha256 前 12 位：生成器一改，五个产物头部即变，会被 --check 捕获 */
const GEN_SHA = sha12(read(__filename));

function banner(title, sources, notes) {
  return [
    '-- ============================================================================',
    `-- ${title}`,
    '-- ----------------------------------------------------------------------------',
    `-- 生成器: ${GEN_REL} sha256=${GEN_SHA}`,
    '-- 确定性: 无墙钟时间戳/随机量；同一输入重复生成逐字节一致（Flyway checksum 稳定）。',
    '-- 请勿手工编辑本文件：改 oa-deploy/sql 或文档后重跑生成器。',
    ...sources.map((s) => `-- 来源: ${s.label} sha256=${s.sha}`),
    ...notes.map((n) => `-- ${n}`),
    '-- ============================================================================',
    '',
  ].join('\n');
}

/** 交付脚本 → {label, sha} 溯源条目 */
function source(label, absPath) {
  return { label, sha: sha12(read(absPath)) };
}

/** 把 01-schema.sql 拆成「建表部分」与「触发器部分」 */
function splitSchema(sql) {
  const lines = sql.split('\n');
  const delimiterLine = lines.findIndex((l) => /^DELIMITER\s+/i.test(l.trim()));
  if (delimiterLine < 0) return { schema: sql, triggers: null };

  // 向上吞掉紧邻的注释行（那段注释属于触发器）
  let start = delimiterLine;
  while (start > 0 && lines[start - 1].trim().startsWith('--')) start--;

  return {
    schema: lines.slice(0, start).join('\n').trimEnd() + '\n',
    triggers: lines.slice(start).join('\n'),
  };
}

/** 触发器段 → 以 // 分隔的语句（去掉 DELIMITER 行） */
function normalizeTriggers(section) {
  const body = section
    .split('\n')
    .filter((l) => !/^DELIMITER\s+/i.test(l.trim()))
    .join('\n');
  const statements = body
    .split('//')
    .map((s) => s.trim())
    .filter((s) => s.length > 0 && /CREATE TRIGGER/i.test(s));
  return statements.map((s) => s.replace(/\s*$/, '')).join('\n//\n') + '\n';
}

function analyze(schemaSql, dictSql, templateSql, triggerSql, permissionSql) {
  const errors = [];
  const count = (sql, re) => (sql.match(re) || []).length;

  const tables = count(schemaSql, /^CREATE TABLE/gm);
  const triggersInSchema = count(schemaSql, /CREATE TRIGGER/gim);
  const delimitersInSchema = count(schemaSql, /^DELIMITER/gim);
  const triggers = count(triggerSql, /CREATE TRIGGER/gim);
  const delimitersInTriggers = count(triggerSql, /^DELIMITER/gim);
  const inserts = count(dictSql, /^INSERT INTO/gm);
  const templateInserts = count(templateSql, /^INSERT INTO/gm);
  const nodeRows = count(templateSql, /'finance_review'|'dept_leader'|'branch_leader'|'subsidiary_gm'|'group_leader'|'chairman'|'archive_register'/g);

  const roleInserts = count(permissionSql, /INSERT INTO sys_role \(/g);
  const permInserts = count(permissionSql, /INSERT INTO sys_permission/g);
  const grantInserts = count(permissionSql, /INSERT INTO sys_role_permission/g);
  const roleCodes = ['admin', 'company_admin', 'employee', 'dept_leader', 'branch_leader',
    'subsidiary_gm', 'finance_owner', 'group_leader', 'chairman'];
  const missingRoles = roleCodes.filter((code) => !permissionSql.includes(`'${code}'`));
  const firstRole = permissionSql.indexOf('INSERT INTO sys_role (');
  const firstPerm = permissionSql.indexOf('INSERT INTO sys_permission');
  const firstGrant = permissionSql.indexOf('INSERT INTO sys_role_permission');

  if (tables !== 28) errors.push(`V1 schema 建表数应为 28，实际 ${tables}`);
  if (triggersInSchema !== 0) errors.push(`V1 schema 仍包含 CREATE TRIGGER（${triggersInSchema}），Flyway 会解析失败`);
  if (delimitersInSchema !== 0) errors.push(`V1 schema 仍包含 DELIMITER（${delimitersInSchema}）`);
  if (triggers !== 4) errors.push(`触发器文件应含 4 个 CREATE TRIGGER，实际 ${triggers}`);
  if (delimitersInTriggers !== 0) errors.push('触发器文件不应含 DELIMITER');
  if (inserts !== 8) errors.push(`V2 字典种子 INSERT 应为 8，实际 ${inserts}`);
  if (templateInserts < 4) errors.push(`V3 模板 INSERT 应 ≥4，实际 ${templateInserts}`);
  if (nodeRows < 28) errors.push(`V3 节点行数应 ≥28，实际 ${nodeRows}`);
  if (roleInserts < 1) errors.push('V4 未播种 sys_role（缺角色时授权会静默插入 0 行）');
  if (missingRoles.length) errors.push(`V4 缺少内置角色码：${missingRoles.join(', ')}`);
  if (permInserts < 90) errors.push(`V4 权限项 INSERT 应 ≥90（当前 ${permInserts}）`);
  if (grantInserts < 40) errors.push(`V4 角色授权语句应 ≥40（当前 ${grantInserts}）`);
  if (!(firstRole >= 0 && firstRole < firstPerm && firstPerm < firstGrant)) {
    errors.push('V4 三段顺序错误：必须「角色 → 权限树 → 角色授权」（授权段在前会 JOIN 不到角色，静默插 0 行）');
  }
  if (permissionSql.includes('admin.user.export') || permissionSql.includes('admin.org.manage')) {
    errors.push('V4 出现点号风格的权限码（应为冒号风格，与前端判据逐字一致）');
  }
  for (const [name, sql] of [['V1', schemaSql], ['V2', dictSql], ['V3', templateSql], ['V4', permissionSql]]) {
    if (!sql.trimEnd().endsWith(';')) errors.push(`${name} 未以分号结尾`);
  }

  return {
    errors,
    stats: {
      tables,
      triggers,
      dictInserts: inserts,
      templateInserts,
      nodeRows,
      roles: roleCodes.length - missingRoles.length,
      permissionItems: permInserts,
      grantStatements: grantInserts,
    },
  };
}

function main() {
  const schemaRaw = read(path.join(SQL_DIR, '01-schema.sql'));
  const dictRaw = read(path.join(SQL_DIR, '02-dict-seed.sql'));
  const templateRaw = read(path.join(SQL_DIR, '03-templates.sql'));
  const permissionRaw = read(path.join(SQL_DIR, '04-permissions.sql'));

  const { schema, triggers } = splitSchema(schemaRaw);
  if (!triggers) {
    console.log(JSON.stringify({ ok: false, errors: ['01-schema.sql 未找到 DELIMITER 触发器段'] }, null, 2));
    process.exit(1);
  }
  const triggerSql = normalizeTriggers(triggers);

  const files = {
    'V1__schema.sql': banner(
      'V1 建表（27 张表；不含触发器，触发器见 db/trigger/immutable-triggers.sql）',
      [source('oa-deploy/sql/01-schema.sql ← doc/data-model.md', path.join(SQL_DIR, '01-schema.sql'))],
      ['执行：Flyway 自动按版本顺序执行 V1 → V2 → V3。', '字符集 utf8mb4 / 引擎 InnoDB；按文档顺序建表，外键依赖已满足。'],
    ) + '\n' + schema.replace(
      /^-- 注意：触发器使用 mysql 客户端语法.*$/m,
      '-- 注意：触发器已拆分到 db/trigger/immutable-triggers.sql，由 ImmutableTriggerInitializer 启动时幂等创建',
    ) + '\n-- （Flyway 不识别 mysql 客户端的 DELIMITER 语法，故不放在本迁移中。）\n',

    'V2__dict_seed.sql': banner(
      'V2 数据字典种子（8 个 dict_type，幂等）',
      [source('oa-deploy/sql/02-dict-seed.sql ← doc/dict-seed.md', path.join(SQL_DIR, '02-dict-seed.sql'))],
      ['可重复执行（ON DUPLICATE KEY UPDATE）。'],
    ) + '\n' + dictRaw.replace(/^-- =+[\s\S]*?SET NAMES utf8mb4;\n\n/, ''),

    'V3__templates.sql': banner(
      'V3 流程模板与表单模板（4 模板 × 7 节点 + 4 份 form_schema_json，幂等）',
      [source('oa-deploy/sql/03-templates.sql ← doc/templates.md / doc/forms.md', path.join(SQL_DIR, '03-templates.sql'))],
      ['可重复执行（ON DUPLICATE KEY UPDATE）。', '④templates.sql 末尾的自检 SELECT 已保留，便于人工核对。'],
    ) + '\n' + templateRaw.replace(/^-- =+[\s\S]*?SET NAMES utf8mb4;\n\n/, ''),

    'V4__permissions.sql': banner(
      'V4 内置角色 + 权限树 + 角色授权（9 角色 / 94 权限项 / 376 授权行，幂等）',
      [source('oa-deploy/sql/04-permissions.sql ← tools/gen-permission-seed.js（数据在此定义）', path.join(SQL_DIR, '04-permissions.sql'))],
      [
        '三段顺序不可调换：① 播种 sys_role（9 个内置角色）→ ② 播种 sys_permission（权限树，父先于子）→ ③ 播种 sys_role_permission。',
        '若角色段被移到授权段之后，授权 JOIN 不到角色会**静默插入 0 行**（表现为登录后没有菜单）——check-permission-seed.js 有顺序断言。',
        '权限码为**冒号风格**（如 admin:user:export），与 oa-web 的前端判据逐字一致。',
        'finance_owner / group_leader 的 data_scope=group_category，还需在 sys_role_category 配事项类别五值（本迁移不播种该表）。',
      ],
    ) + '\n' + permissionRaw.replace(/^-- =+[\s\S]*?SET NAMES utf8mb4;\n\n/, ''),
  };

  const triggerFile = banner(
    '不可篡改触发器（sys_log / flow_signature：拒绝 UPDATE 与 DELETE，AC-20）',
    [source('oa-deploy/sql/01-schema.sql 的 DELIMITER 段', path.join(SQL_DIR, '01-schema.sql'))],
    [
      '本文件不是 Flyway 迁移：由 com.oa.platform.bootstrap.ImmutableTriggerInitializer 在启动时读取，',
      '按「单独成行的双斜杠」切分为独立语句，逐条检查 information_schema.TRIGGERS 后 **幂等创建缺失项**。',
      '注意：本文件的注释里**不要出现字面量的双斜杠**（历史上曾因此误切、吞掉一条 CREATE TRIGGER，',
      '导致 sys_log 的改保护静默缺失）；解析器已改为行锚定切分并自带条数自检。',
      '原因：Flyway 的 MySQL 解析器不识别 mysql 客户端的 DELIMITER 语法。',
    ],
  ) + '\n' + triggerSql;

  const { errors, stats } = analyze(schema, files['V2__dict_seed.sql'], files['V3__templates.sql'], triggerSql, files['V4__permissions.sql']);
  const report = { ok: errors.length === 0, check: CHECK, stats, errors, files: [] };

  const targets = [
    ...Object.entries(files).map(([name, content]) => [path.join(MIGRATION_DIR, name), content]),
    [path.join(TRIGGER_DIR, 'immutable-triggers.sql'), triggerFile],
  ];

  for (const [file, content] of targets) {
    const rel = path.relative(ROOT, file).replace(/\\/g, '/');
    const exists = fs.existsSync(file);
    // 精确比较（不再对「生成时间」行做归一化抹平）：产物已确定，磁盘 == 生成结果 才是 upToDate
    const same = exists && read(file) === content;
    report.files.push({
      path: rel,
      exists,
      upToDate: same,
      bytes: Buffer.byteLength(content, 'utf8'),
      sha256: crypto.createHash('sha256').update(content, 'utf8').digest('hex'),
    });
    if (CHECK && exists && !same) errors.push(`${rel} 与交付脚本不一致（需重新生成）`);
    if (!CHECK && !same) {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, content, 'utf8');
    }
  }

  report.ok = errors.length === 0;
  console.log(JSON.stringify(report, null, 2));
  process.exit(report.ok ? 0 : 1);
}

main();
