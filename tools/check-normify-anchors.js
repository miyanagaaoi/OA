#!/usr/bin/env node
/*
 * check-normify-anchors.js —— 结构基线「证据引用锚点化」机检器（零依赖，仅 Node 内置模块）
 *
 * 背景：模块 frontmatter 的 `source` 指向 doc/*.md 时，早期用 `line`/`end_line` 定位。
 * 行号是「位置坐标」，文档任何增删都会整体漂移，而 normify_module_refresh 只重算
 * fingerprint/revision、不重算行号，漂移对 normify_validate 完全不可见。
 * 现在改为：doc 引用只写 `path`，正文用 `## 证据锚点` 节给出内容锚点：
 *
 *   ## 证据锚点
 *   - `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
 *   - `doc/data-model.md` → `CREATE TABLE sys_thread`（§6. 签名、附件、抄送、消息、审计）
 *   - `doc/forms.md`（锚点待定：§1.4 附件通用限制）        <-- 未定锚点，机检记 warning
 *
 * 锚点取值：REQ 编号优先；无 REQ 时用章节标题原文（如 `### 7.2 状态机`）；
 * DDL 引用用 `CREATE TABLE <表名>`。代码引用（oa-server/**、oa-web/**、tools/**…）
 * 仍保留 source 里的 line/end_line，正文不必重复。锚点只需一份，同时服务 zh/en 描述。
 *
 * 断言：
 *   1. doc 引用不得带行号：source 中 path 以 `doc/` 开头的条目出现 line/end_line 即 error。
 *   2. 每条 doc 引用都要有对应锚点条目：正文 `## 证据锚点` 里存在 path 相同的条目，
 *      否则 error；条目存在但标注「锚点待定」记 warning（不阻断）。
 *   3. 锚点必须真的存在于该文档：锚点文本（去掉反引号包裹与尾部「（§…）」说明）能在
 *      该 doc 文件里找到，找不到即 error。
 *   4. 锚点唯一性：锚点在文档中出现 0 次 → error（同 3）；>1 次 → warning（提示换更精确锚点）。
 *      产线文档里 REQ 编号天然被「定义处 + 验收处 + 追溯表」多处引用，这类 warning 属预期；
 *      输出按 (文档, 锚点) 聚合，避免刷屏。
 *   5. 输出 { ok, errors, warnings, stats } 结构；有 error 时 exit 1。
 *
 * 用法：
 *   node tools/check-normify-anchors.js --check                 # 只读校验（默认即只读）
 *   node tools/check-normify-anchors.js --check --json          # 机器可读输出
 *   node tools/check-normify-anchors.js --repo H:\dsh\OA --dir normify-oa
 */
'use strict';

const fs = require('fs');
const path = require('path');

const DOC_PREFIX = 'doc/';
const ANCHOR_HEADING = /^##\s+证据锚点\s*$/m;
const NOTE_MARK = '（§';
const PENDING_MARK = '锚点待定';

function parseArgs(argv) {
  const opts = { repo: process.cwd(), dir: null, json: false, check: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--check') opts.check = true;
    else if (a === '--json') opts.json = true;
    else if (a === '--repo') opts.repo = argv[++i];
    else if (a === '--dir') opts.dir = argv[++i];
    else if (a === '-h' || a === '--help') { opts.help = true; }
    else { console.error('未知参数：' + a + '（支持 --check / --json / --repo <dir> / --dir <dir>）'); process.exit(2); }
  }
  opts.repo = path.resolve(opts.repo);
  opts.dir = opts.dir ? (path.isAbsolute(opts.dir) ? opts.dir : path.join(opts.repo, opts.dir)) : path.join(opts.repo, 'normify-oa');
  return opts;
}

function walk(dir, out = []) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.isFile() && e.name.endsWith('.md')) out.push(p);
  }
  return out;
}

const unquote = s => s.replace(/^["']|["']$/g, '').trim();

/** 轻量解析模块文件：id + source 条目 + 正文。仅识别本校验器需要的字段。 */
function parseModule(text) {
  const fm = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!fm) return null;
  const lines = fm[1].split(/\r?\n/);
  const source = [];
  let id = null, inSource = false;
  for (const l of lines) {
    if (inSource) {
      const pm = l.match(/^\s*-\s*path:\s*(.+?)\s*$/);
      if (pm) { source.push({ path: unquote(pm[1]) }); continue; }
      const lm = l.match(/^\s*line:\s*(\d+)\s*$/);
      if (lm && source.length) { source[source.length - 1].line = Number(lm[1]); continue; }
      const em = l.match(/^\s*end_line:\s*(\d+)\s*$/);
      if (em && source.length) { source[source.length - 1].end_line = Number(em[1]); continue; }
    }
    if (/^source:\s*$/.test(l)) { inSource = true; continue; }
    if (/^\S/.test(l)) inSource = false;
    const im = l.match(/^id:\s*(.+?)\s*$/);
    if (im) id = unquote(im[1]);
  }
  const body = text.slice(fm[0].length);
  return { id, source, body };
}

/**
 * 解析正文 `## 证据锚点` 节。
 * 条目形如： - `path` → `anchor`（§说明） / - `path`（锚点待定：§候选章节）
 * 注意：锚点本身可能含反引号（章节标题里就有 `code`），因此不能简单按反引号切分——
 * 说明部分固定以「（§」或「（锚点待定」起头，据此从右侧切。
 */
function parseAnchors(body) {
  const entries = [];
  const m = body.match(ANCHOR_HEADING);
  if (!m) return { found: false, entries };
  const rest = body.slice(m.index + m[0].length);
  const lines = rest.split(/\r?\n/);
  for (const line of lines) {
    const t = line.trim();
    if (t === '') continue;
    if (!t.startsWith('-')) break; // 节内其它内容，遇到非条目即结束
    const pm = t.match(/^-\s*`([^`]+)`/);
    if (!pm) { entries.push({ raw: t, path: null, malformed: true }); continue; }
    const p = pm[1];
    let remainder = t.slice(pm[0].length).trim();
    if (remainder === '') { entries.push({ raw: t, path: p, anchor: null, pending: true, note: '' }); continue; }
    if (!remainder.startsWith('→')) { entries.push({ raw: t, path: p, anchor: null, pending: true, note: remainder.replace(/^[（(]|[）)]$/g, '') }); continue; }
    remainder = remainder.slice(1).trim();
    let anchorRaw = remainder, note = '';
    const cut = remainder.lastIndexOf(NOTE_MARK);
    if (cut >= 0) { anchorRaw = remainder.slice(0, cut).trim(); note = remainder.slice(cut).replace(/^[（(]|[）)]$/g, ''); }
    else if (remainder.endsWith('）') || remainder.endsWith(')')) {
      const c2 = remainder.lastIndexOf('（');
      if (c2 >= 0 && remainder.slice(c2).includes(PENDING_MARK)) { anchorRaw = remainder.slice(0, c2).trim(); note = remainder.slice(c2 + 1, -1); }
    }
    let anchor = anchorRaw;
    if (anchor.startsWith('`') && anchor.endsWith('`') && anchor.length >= 2) anchor = anchor.slice(1, -1);
    const pending = anchor === '' || remainder.includes(PENDING_MARK);
    entries.push({ raw: t, path: p, anchor: pending ? null : anchor, pending, note });
  }
  return { found: true, entries };
}

function countOccurrences(haystack, needle) {
  if (!needle) return 0;
  let n = 0, i = 0;
  for (;;) {
    const j = haystack.indexOf(needle, i);
    if (j < 0) break;
    n++; i = j + needle.length;
  }
  return n;
}

function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.help) {
    console.log('用法：node tools/check-normify-anchors.js [--check] [--json] [--repo <repoRoot>] [--dir <normifyDir>]');
    return 0;
  }
  const errors = [];
  const warnings = [];
  const stats = {
    repo: opts.repo, structureDir: opts.dir,
    modulesScanned: 0, modulesWithDocRefs: 0, docRefs: 0,
    anchorEntries: 0, anchorsResolved: 0, anchorsPending: 0,
    anchorSections: 0, uniqueAnchors: 0, docFiles: 0
  };
  const docCache = new Map();
  const readDoc = rel => {
    if (docCache.has(rel)) return docCache.get(rel);
    const abs = path.join(opts.repo, rel);
    let text = null;
    try { text = fs.readFileSync(abs, 'utf8'); } catch { text = null; }
    docCache.set(rel, text);
    return text;
  };

  const files = walk(path.join(opts.dir, 'modules'));
  if (files.length === 0) {
    errors.push({ code: 'structure/no-modules', message: '未找到任何模块文件：' + path.join(opts.dir, 'modules') });
  }
  const agg = new Map(); // key `${path}\u0000${anchor}` -> { path, anchor, count, modules:Set }

  for (const f of files) {
    const rel = path.relative(opts.repo, f).split(path.sep).join('/');
    const mod = parseModule(fs.readFileSync(f, 'utf8'));
    if (!mod || !mod.id) { errors.push({ code: 'module/unparsable', file: rel, message: '模块文件缺少 frontmatter 或 id' }); continue; }
    stats.modulesScanned++;
    const docRefs = mod.source.filter(s => s.path.startsWith(DOC_PREFIX));
    if (docRefs.length === 0) continue;
    stats.modulesWithDocRefs++;
    stats.docRefs += docRefs.length;

    // 断言 1：doc 引用不得带行号
    for (const s of docRefs) {
      if (s.line !== undefined || s.end_line !== undefined) {
        errors.push({
          code: 'anchor/doc-ref-has-line', module: mod.id, file: rel, path: s.path,
          message: 'doc 引用仍带行号（line=' + (s.line ?? '-') + ', end_line=' + (s.end_line ?? '-') + '）：行号会随文档增删漂移，应改为正文 `## 证据锚点`',
          fix: '删除 source 中该条目的 line/end_line，并在正文 `## 证据锚点` 补内容锚点'
        });
      }
    }

    const parsed = parseAnchors(mod.body);
    if (!parsed.found) {
      errors.push({
        code: 'anchor/section-missing', module: mod.id, file: rel,
        message: '模块声明了 doc 引用，但正文缺少 `## 证据锚点` 节',
        fix: '在正文末尾追加 `## 证据锚点`，每个 doc 路径一行：- `doc/x.md` → `<锚点>`（§章节）'
      });
      continue;
    }
    stats.anchorSections++;
    const byPath = new Map();
    for (const e of parsed.entries) {
      stats.anchorEntries++;
      if (!e.path) { errors.push({ code: 'anchor/malformed-entry', module: mod.id, file: rel, message: '锚点条目格式无法解析：' + e.raw }); continue; }
      if (!byPath.has(e.path)) byPath.set(e.path, []);
      byPath.get(e.path).push(e);
    }

    for (const s of docRefs) {
      const list = byPath.get(s.path);
      // 断言 2：每条 doc 引用都有锚点条目
      if (!list || list.length === 0) {
        errors.push({
          code: 'anchor/missing-for-doc-ref', module: mod.id, file: rel, path: s.path,
          message: 'source 里的 doc 引用在 `## 证据锚点` 中没有对应条目',
          fix: '补一行：- `' + s.path + '` → `<该模块内容对应的 REQ 编号/章节标题/CREATE TABLE 表名>`'
        });
        continue;
      }
      const resolved = list.filter(e => !e.pending && e.anchor);
      if (resolved.length === 0) {
        stats.anchorsPending += list.length;
        warnings.push({
          code: 'anchor/pending', module: mod.id, file: rel, path: s.path,
          message: 'doc 引用已标注「锚点待定」，尚未给出可机检锚点' + (list[0].note ? '（候选：' + list[0].note + '）' : ''),
          fix: '确认候选章节后改成 - `' + s.path + '` → `<锚点>`（§章节）'
        });
        continue;
      }
      const docText = readDoc(s.path);
      if (docText === null) {
        errors.push({ code: 'anchor/doc-file-missing', module: mod.id, file: rel, path: s.path, message: '引用的文档在仓库中不存在：' + s.path });
        continue;
      }
      for (const e of resolved) {
        stats.anchorsResolved++;
        const n = countOccurrences(docText, e.anchor);
        // 断言 3：锚点必须存在于该文档；断言 4：>1 次记 warning
        if (n === 0) {
          errors.push({
            code: 'anchor/not-found', module: mod.id, file: rel, path: s.path, anchor: e.anchor,
            message: '锚点在文档中找不到（0 次）：' + JSON.stringify(e.anchor),
            fix: '核对锚点原文（REQ 编号 / 章节标题原文 / `CREATE TABLE <表名>`），或改用更精确的锚点'
          });
          continue;
        }
        const key = s.path + '\u0000' + e.anchor;
        if (!agg.has(key)) agg.set(key, { path: s.path, anchor: e.anchor, count: n, modules: new Set() });
        agg.get(key).modules.add(mod.id);
      }
    }

    // 附加：锚点节里出现 source 未声明的 doc 路径 → warning（防止改名/删除后残留）
    for (const [p, list] of byPath) {
      if (p.startsWith(DOC_PREFIX) && !docRefs.some(s => s.path === p)) {
        warnings.push({ code: 'anchor/orphan-entry', module: mod.id, file: rel, path: p, message: '`## 证据锚点` 里的 doc 路径未出现在 source 中' });
      }
    }
  }

  // 聚合唯一性 warning
  const dupes = [...agg.values()].filter(x => x.count > 1).sort((a, b) => b.count - a.count);
  for (const d of dupes) {
    warnings.push({
      code: 'anchor/not-unique', path: d.path, anchor: d.anchor, occurrences: d.count, modules: [...d.modules].sort(),
      message: '锚点在该文档中出现 ' + d.count + ' 次（' + [...d.modules].sort().join(', ') + '）：' + JSON.stringify(d.anchor),
      fix: '若需唯一定位，改用更精确的锚点（例如带小节号的标题原文）'
    });
  }
  stats.uniqueAnchors = agg.size;
  stats.docFiles = docCache.size;

  const result = {
    ok: errors.length === 0,
    errors,
    warnings,
    stats: Object.assign({}, stats, { errors: errors.length, warnings: warnings.length })
  };

  if (opts.json) {
    console.log(JSON.stringify(result, null, 2));
  } else {
    console.log('check-normify-anchors：结构基线证据引用锚点化校验');
    console.log('  结构目录 : ' + stats.structureDir);
    console.log('  仓库根   : ' + stats.repo);
    console.log('  扫描模块 : ' + stats.modulesScanned + '（含 doc 引用 ' + stats.modulesWithDocRefs + '，doc 引用条目 ' + stats.docRefs + '）');
    console.log('  锚点节   : ' + stats.anchorSections + '；锚点条目 ' + stats.anchorEntries + '（已解析 ' + stats.anchorsResolved + '，待定 ' + stats.anchorsPending + '）');
    console.log('  唯一锚点 : ' + stats.uniqueAnchors + '；涉及文档 ' + stats.docFiles);
    if (errors.length) {
      console.log('\nERROR（' + errors.length + '）：');
      for (const e of errors) console.log('  [' + e.code + '] ' + (e.module ? e.module + ' | ' : '') + (e.path ? e.path + ' | ' : '') + (e.anchor ? JSON.stringify(e.anchor) + ' | ' : '') + e.message);
    }
    if (warnings.length) {
      console.log('\nWARNING（' + warnings.length + '）：');
      for (const w of warnings) console.log('  [' + w.code + '] ' + (w.module ? w.module + ' | ' : '') + (w.path ? w.path + ' | ' : '') + (w.anchor ? JSON.stringify(w.anchor) + ' | ' : '') + w.message);
    }
    console.log('\n结果：' + (result.ok ? 'ok（0 error）' : 'FAILED（' + errors.length + ' error）') + '，warning ' + warnings.length);
  }
  return errors.length ? 1 : 0;
}

process.exit(main());
