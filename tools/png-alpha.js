// PNG 白底转透明（零依赖）：RGB → RGBA，alpha = 从白到前景色的投影
// 用法: node png-alpha.js <in.png> <out.png> [outWidth]
//   - 白 → 完全透明；纯前景红 → 完全不透明；红白抗锯齿 → 半透明（颜色不变）
//   - 输出为 RGBA、filter=0、单一 IDAT
//   - 可选 outWidth：等比缩放（盒式滤波），用于生成界面实际需要的尺寸
const fs = require('fs');
const zlib = require('zlib');

const [IN, OUT, W_ARG] = process.argv.slice(2);

/* ---------- 读 PNG（8bit RGB/RGBA、非隔行） ---------- */
function decode(file) {
  const b = fs.readFileSync(file);
  let off = 8, W = 0, H = 0, bitDepth = 0, colorType = 0, interlace = 0;
  const idat = [];
  while (off < b.length) {
    const len = b.readUInt32BE(off);
    const type = b.subarray(off + 4, off + 8).toString('ascii');
    const data = b.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      W = data.readUInt32BE(0); H = data.readUInt32BE(4);
      bitDepth = data[8]; colorType = data[9]; interlace = data[12];
    } else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    off += 12 + len;
  }
  if (bitDepth !== 8 || interlace !== 0 || (colorType !== 2 && colorType !== 6)) {
    throw new Error(`不支持: bitDepth=${bitDepth} colorType=${colorType} interlace=${interlace}`);
  }
  const ch = colorType === 6 ? 4 : 3;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = W * ch;
  const out = Buffer.alloc(W * H * ch);
  let prev = Buffer.alloc(stride);
  let pos = 0;
  for (let y = 0; y < H; y++) {
    const ft = raw[pos++];
    const line = raw.subarray(pos, pos + stride); pos += stride;
    const cur = Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= ch ? cur[i - ch] : 0;
      const bb = prev[i];
      const c = i >= ch ? prev[i - ch] : 0;
      let v = line[i];
      if (ft === 1) v += a;
      else if (ft === 2) v += bb;
      else if (ft === 3) v += (a + bb) >> 1;
      else if (ft === 4) {
        const pp = a + bb - c, pa = Math.abs(pp - a), pb = Math.abs(pp - bb), pc = Math.abs(pp - c);
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? bb : c);
      }
      cur[i] = v & 0xff;
    }
    cur.copy(out, y * stride);
    prev = cur;
  }
  return { W, H, ch, data: out };
}

/* ---------- 白底转透明 ---------- */
// 阈值吸附：JPEG 压缩残留的"近白"像素（如 254,254,254）会算出 alpha 1–2，
// 肉眼不可见但会留下大片"伪半透明"并把背景压在 alpha≠0，导致真透明区域缺失。
// 因此低于阈值的 alpha 直接吸附为 0（并把 RGB 归一为白，避免边缘偏色）。
const ALPHA_CUT = Number(process.env.ALPHA_CUT || 10);
function toAlpha(img, fg) {
  const { W, H, ch, data } = img;
  const rgba = Buffer.alloc(W * H * 4);
  for (let i = 0; i < W * H; i++) {
    const r = data[i * ch], g = data[i * ch + 1], b = data[i * ch + 2];
    // 投影到「白 → 前景」轴线：t=1 为纯前景
    const dr = r - 255, dg = g - 255, db = b - 255;
    const fr = fg[0] - 255, fgn = fg[1] - 255, fb = fg[2] - 255;
    const denom = fr * fr + fgn * fgn + fb * fb;
    let t = (dr * fr + dg * fgn + db * fb) / denom;
    if (t < 0) t = 0; if (t > 1) t = 1;
    let a = Math.round(t * 255);
    if (a < ALPHA_CUT) a = 0;            // 吸附为完全透明
    rgba[i * 4] = r; rgba[i * 4 + 1] = g; rgba[i * 4 + 2] = b; rgba[i * 4 + 3] = a;
    if (a === 0) { rgba[i * 4] = 255; rgba[i * 4 + 1] = 255; rgba[i * 4 + 2] = 255; }
  }
  return { W, H, data: rgba };
}

/* ---------- 盒式缩放（按覆盖率累积 alpha，避免透明区被糊成半透明） ---------- */
// 关键：每个输出像素的 alpha 是所有源像素 alpha 的**平均**（即面积覆盖率）。
// 正是这一点让大片透明区保持 alpha=0（若改用"按 alpha 加权平均"，整片透明区
// 会因为权重和为 0 而落到某个默认值，从而出现半透明雾面）。
// RGB 则按 alpha 加权，避免透明像素的颜色污染边缘。
function scale(img, outW) {
  const inW = img.W, inH = img.H;
  const outH = Math.max(1, Math.round(inH * outW / inW));
  const sx = inW / outW, sy = inH / outH;
  const dst = Buffer.alloc(outW * outH * 4);
  for (let y = 0; y < outH; y++) {
    for (let x = 0; x < outW; x++) {
      const x0 = Math.floor(x * sx), x1 = Math.max(x0 + 1, Math.floor((x + 1) * sx));
      const y0 = Math.floor(y * sy), y1 = Math.max(y0 + 1, Math.floor((y + 1) * sy));
      let rs = 0, gs = 0, bs = 0, as = 0, wsum = 0, n = 0;
      for (let yy = y0; yy < y1 && yy < inH; yy++) {
        for (let xx = x0; xx < x1 && xx < inW; xx++) {
          const i = (yy * inW + xx) * 4;
          const a = img.data[i + 3] / 255;
          rs += img.data[i] * a; gs += img.data[i + 1] * a; bs += img.data[i + 2] * a;
          as += a; wsum += a; n++;              // as 累加的是 alpha 本身（覆分覆盖率的分子）
        }
      }
      const o = (y * outW + x) * 4;
      if (wsum > 0) {
        dst[o] = Math.round(rs / wsum); dst[o + 1] = Math.round(gs / wsum); dst[o + 2] = Math.round(bs / wsum);
      } else { dst[o] = 255; dst[o + 1] = 255; dst[o + 2] = 255; }
      dst[o + 3] = Math.round((as / n) * 255);   // 覆盖率平均 → 透明区恒为 0
    }
  }
  return { W: outW, H: outH, data: dst };
}

/* ---------- 写 PNG（RGBA, filter=0） ---------- */
function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c = (crc ^ buf[i]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encode(img) {
  const { W, H, data } = img;
  const stride = W * 4;
  // filter=1（Sub 滤波）：对本图这类大片同色区域压缩率远好于 filter=0
  const raw = Buffer.alloc((stride + 1) * H);
  for (let y = 0; y < H; y++) {
    const ro = y * (stride + 1);
    raw[ro] = 1;
    const so = y * stride;
    for (let i = 0; i < stride; i++) {
      const a = i >= 4 ? data[so + i - 4] : 0;
      raw[ro + 1 + i] = (data[so + i] - a) & 0xff;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/* ---------- 主流程 ---------- */
const path = require('path');
// 防呆：输入与输出指向同一文件时直接拒绝（避免把原图覆盖掉）
if (path.resolve(IN) === path.resolve(OUT)) {
  console.error(`拒绝执行：输入与输出是同一个文件（${IN}）。请指定不同的输出路径。`);
  process.exit(2);
}
const img = decode(IN);
// 自动取前景色：出现次数最多的非白颜色（量化到 16 级）
const counts = new Map();
const { W, H, ch, data } = img;
for (let i = 0; i < W * H; i += 1) {
  const r = data[i * ch], g = data[i * ch + 1], b = data[i * ch + 2];
  if (r > 245 && g > 245 && b > 245) continue;
  const k = `${r >> 4},${g >> 4},${b >> 4}`;
  counts.set(k, (counts.get(k) || 0) + 1);
}
const top = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
const fg = top ? top[0].split(',').map(v => (parseInt(v, 10) << 4) + 8) : [237, 28, 36];
console.log(`输入 ${W}x${H} | 检出前景色 ≈ rgb(${fg.join(',')})`);

let out = toAlpha(img, fg);
if (W_ARG) { out = scale(out, parseInt(W_ARG, 10)); console.log(`缩放至 ${out.W}x${out.H}`); }
const png = encode(out);
fs.writeFileSync(OUT, png);

// 透明度统计
let t0 = 0, t255 = 0, half = 0;
for (let i = 0; i < out.W * out.H; i++) {
  const a = out.data[i * 4 + 3];
  if (a === 0) t0++; else if (a === 255) t255++; else half++;
}
const tot = out.W * out.H;
console.log(`输出 ${out.W}x${out.H} RGBA | ${(png.length / 1024).toFixed(1)} KB`);
console.log(`  完全透明 ${(t0 / tot * 100).toFixed(1)}% | 完全不透明 ${(t255 / tot * 100).toFixed(1)}% | 半透明(抗锯齿) ${(half / tot * 100).toFixed(2)}%`);
