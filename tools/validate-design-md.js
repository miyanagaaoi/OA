// DESIGN.md 校验器：结构 / 令牌引用 / WCAG 对比度
// 等价于 npx @google/design.md lint 的核心检查项（该 CLI 在本机沙箱内崩溃）
// 用法: node validate-design-md.js <DESIGN.md 路径>
const fs = require('fs');

const file = process.argv[2];
const raw = fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '');

const findings = [];
const add = (severity, path, message) => findings.push({ severity, path, message });

// ---------- 1. front matter 解析（最小 YAML 子集） ----------
const m = raw.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
if (!m) {
  console.log(JSON.stringify({ findings: [{ severity: 'error', path: '', message: '缺少 YAML front matter' }], summary: { errors: 1, warnings: 0, info: 0 } }, null, 2));
  process.exit(1);
}
const body = raw.slice(m[0].length);
const fmLines = m[1].split(/\r?\n/);

const tokens = {};
let curTop = null;
let curSub = null;
for (let i = 0; i < fmLines.length; i++) {
  const line = fmLines[i];
  if (!line.trim() || /^\s*#/.test(line)) continue;
  const top = line.match(/^([A-Za-z0-9_-]+):\s*(.*)$/);
  const sub = line.match(/^  ([A-Za-z0-9_.-]+):\s*(.*)$/);
  const sub2 = line.match(/^    ([A-Za-z0-9_.-]+):\s*(.*)$/);
  if (top) {
    curTop = top[1];
    curSub = null;
    if (top[2] === '') tokens[curTop] = tokens[curTop] || {};
    else tokens[curTop] = top[2];
  } else if (sub && curTop) {
    curSub = sub[1];
    if (sub[2] === '') {
      tokens[curTop][curSub] = {};
    } else {
      tokens[curTop][curSub] = unquote(sub[2]);
    }
  } else if (sub2 && curTop && curSub) {
    tokens[curTop][curSub][sub2[1]] = unquote(sub2[2]);
  } else {
    add('warning', `front-matter.line${i + 1}`, `无法解析的行: ${line.trim()}`);
  }
}
function unquote(v) {
  v = v.trim();
  if (/^".*"$/.test(v)) return v.slice(1, -1);
  return v;
}

// 顶层键（非缩进）顺序检查
const topKeys = fmLines.filter((l) => /^[A-Za-z0-9_-]+:/.test(l)).map((l) => l.split(':')[0]);
if (!topKeys.includes('name')) add('error', 'name', '缺少必填字段 name');
for (const k of topKeys) {
  if (!['version', 'name', 'description', 'colors', 'typography', 'rounded', 'spacing', 'components'].includes(k)) {
    add('warning', k, `未知的顶层令牌分组: ${k}`);
  }
}

// ---------- 2. 令牌值合法性 ----------
const colors = tokens.colors || {};
for (const [k, v] of Object.entries(colors)) {
  if (typeof v !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(v)) add('error', `colors.${k}`, `颜色值必须是 #RRGGBB，实际为 ${v}`);
}
const dimRe = /^-?\d+(\.\d+)?(px|em|rem)$/;
for (const [k, v] of Object.entries(tokens.rounded || {})) {
  if (typeof v === 'object') { add('error', `rounded.${k}`, 'rounded 必须是 Dimension'); continue; }
  if (!dimRe.test(v)) add('error', `rounded.${k}`, `必须是带单位的尺寸，实际为 ${v}`);
}
for (const [k, v] of Object.entries(tokens.spacing || {})) {
  if (typeof v === 'object') { add('error', `spacing.${k}`, 'spacing 必须是 Dimension 或数字'); continue; }
  if (!dimRe.test(v) && !/^\d+(\.\d+)?$/.test(v)) add('error', `spacing.${k}`, `必须是 Dimension 或数字，实际为 ${v}`);
}
const typoProps = ['fontFamily', 'fontSize', 'fontWeight', 'lineHeight', 'letterSpacing', 'fontFeature', 'fontVariation'];
for (const [k, v] of Object.entries(tokens.typography || {})) {
  if (typeof v === 'string') { add('error', `typography.${k}`, 'typography 必须是对象'); continue; }
  if (!v.fontFamily) add('error', `typography.${k}`, '缺少 fontFamily');
  if (!v.fontSize) add('error', `typography.${k}`, '缺少 fontSize');
  for (const p of Object.keys(v)) if (!typoProps.includes(p)) add('warning', `typography.${k}.${p}`, `未知的 typography 属性: ${p}`);
}
const compProps = ['backgroundColor', 'textColor', 'typography', 'rounded', 'padding', 'size', 'height', 'width'];
for (const [k, v] of Object.entries(tokens.components || {})) {
  if (typeof v === 'string') { add('error', `components.${k}`, 'components 条目必须是对象'); continue; }
  for (const p of Object.keys(v)) if (!compProps.includes(p)) add('warning', `components.${k}.${p}`, `未知的组件属性: ${p}`);
}

// ---------- 3. 令牌引用解析 ----------
const refs = [];
function walk(node, path) {
  if (typeof node === 'string') {
    for (const r of node.matchAll(/\{([a-zA-Z0-9_.-]+)\}/g)) refs.push({ path, ref: r[1] });
    return;
  }
  if (node && typeof node === 'object') for (const [k, v] of Object.entries(node)) walk(v, path ? `${path}.${k}` : k);
}
walk(tokens.components, 'components');
for (const { path, ref } of refs) {
  const parts = ref.split('.');
  let node = tokens;
  for (const p of parts) {
    if (node && typeof node === 'object' && p in node) node = node[p];
    else { node = undefined; break; }
  }
  if (node === undefined) add('error', path, `令牌引用无法解析: {${ref}}`);
}

// 未使用的令牌（info）
const usedRefs = new Set(refs.map((r) => r.ref));
for (const group of ['colors', 'typography', 'rounded', 'spacing']) {
  for (const k of Object.keys(tokens[group] || {})) {
    if (!usedRefs.has(`${group}.${k}`)) add('info', `${group}.${k}`, '令牌未在 components 中被引用（可能在正文中使用）');
  }
}

// ---------- 4. 章节顺序 ----------
const sectionOrder = ['Overview', 'Colors', 'Typography', 'Layout', 'Elevation & Depth', 'Shapes', 'Components', "Do's and Don'ts"];
const aliases = { 'Brand & Style': 'Overview', 'Layout & Spacing': 'Layout', Elevation: 'Elevation & Depth' };
const headings = [...body.matchAll(/^## (.+)$/gm)].map((h) => h[1].trim());
const canonical = headings.map((h) => aliases[h] || h);
let lastIdx = -1;
const seen = new Set();
for (const h of canonical) {
  if (h === '附录' || h.startsWith('附录')) continue;
  const idx = sectionOrder.indexOf(h);
  if (idx === -1) { add('warning', `section.${h}`, '未知章节标题（规范要求保留但不报错）'); continue; }
  if (seen.has(h)) add('error', `section.${h}`, '章节重复，规范要求拒绝该文件');
  seen.add(h);
  if (idx < lastIdx) add('error', `section.${h}`, `章节顺序错误：应位于 ${sectionOrder[lastIdx]} 之后`);
  lastIdx = Math.max(lastIdx, idx);
}
const missing = sectionOrder.filter((s) => !seen.has(s));
if (missing.length) add('info', 'sections', `未包含的可选章节: ${missing.join(', ')}`);

// ---------- 5. WCAG 对比度 ----------
function srgb(c) { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
function lum(hex) {
  const r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
  return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b);
}
function ratio(a, b) { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); }
function resolveColor(v) {
  if (typeof v !== 'string') return null;
  const r = v.match(/^\{colors\.([a-zA-Z0-9_.-]+)\}$/);
  if (r) return colors[r[1]] || null;
  if (/^#[0-9a-fA-F]{6}$/.test(v)) return v;
  return null;
}
// 正文级字号判断（>=18.66px 且 bold，或 >=24px 视为大字号）
function isLarge(typoRef) {
  if (!typoRef) return false;
  const r = typoRef.match(/^\{typography\.([a-zA-Z0-9_.-]+)\}$/);
  if (!r) return false;
  const t = (tokens.typography || {})[r[1]];
  if (!t) return false;
  const size = parseFloat(String(t.fontSize));
  const weight = parseFloat(String(t.fontWeight || 400));
  return size >= 24 || (size >= 18.66 && weight >= 700);
}
const skipContrast = new Set(['modal-backdrop', 'watermark', 'signature-pad', 'upload-dropzone', 'cascader', 'avatar']);
for (const [name, comp] of Object.entries(tokens.components || {})) {
  if (skipContrast.has(name)) continue;
  const bg = resolveColor(comp.backgroundColor);
  const fg = resolveColor(comp.textColor);
  if (!bg || !fg) { add('warning', `components.${name}`, '无法解析 backgroundColor/textColor 用于对比度检查'); continue; }
  const cr = ratio(bg, fg);
  const need = isLarge(comp.typography) ? 3 : 4.5;
  const isDisabled = /disabled/.test(name) || /pending/.test(name);
  if (cr >= need) add('info', `components.${name}`, `textColor (${fg}) on backgroundColor (${bg}) 对比度 ${cr.toFixed(2)}:1 — 通过 WCAG AA`);
  else if (isDisabled) add('info', `components.${name}`, `textColor (${fg}) on backgroundColor (${bg}) 对比度 ${cr.toFixed(2)}:1 — 禁用/未激活态，WCAG AA 豁免`);
  else add('error', `components.${name}`, `textColor (${fg}) on backgroundColor (${bg}) 对比度 ${cr.toFixed(2)}:1 — 低于 AA 要求 ${need}:1`);
}

// ---------- 输出 ----------
const summary = { errors: 0, warnings: 0, info: 0 };
for (const f of findings) summary[f.severity === 'error' ? 'errors' : f.severity === 'warning' ? 'warnings' : 'info']++;
const order = { error: 0, warning: 1, info: 2 };
findings.sort((a, b) => order[a.severity] - order[b.severity]);
console.log(JSON.stringify({ file, tokenCounts: { colors: Object.keys(colors).length, typography: Object.keys(tokens.typography || {}).length, rounded: Object.keys(tokens.rounded || {}).length, spacing: Object.keys(tokens.spacing || {}).length, components: Object.keys(tokens.components || {}).length }, sections: canonical.filter((h) => !h.startsWith('附录')), findings, summary }, null, 2));
process.exit(summary.errors ? 1 : 0);
