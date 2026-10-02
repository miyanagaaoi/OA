// PNG 像素分布探针：为"抠白底"选择合适的 alpha 算法
const fs = require('fs');
const zlib = require('zlib');

const p = process.argv[2];
const b = fs.readFileSync(p);

// --- 读 IHDR + 合并 IDAT ---
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
if (bitDepth !== 8 || (colorType !== 2 && colorType !== 6) || interlace !== 0) {
  console.log('本探针只支持 8bit RGB/RGBA 非隔行；实际:', { bitDepth, colorType, interlace });
}
const raw = zlib.inflateSync(Buffer.concat(idat));

// --- 逐行反过滤 ---
const ch = colorType === 6 ? 4 : 3;
const bpp = ch, stride = W * bpp;
const px = Buffer.alloc(W * H * bpp);
let prev = Buffer.alloc(stride);
let pos = 0;
for (let y = 0; y < H; y++) {
  const ft = raw[pos++];
  const line = raw.subarray(pos, pos + stride); pos += stride;
  const cur = Buffer.alloc(stride);
  for (let i = 0; i < stride; i++) {
    const a = i >= bpp ? cur[i - bpp] : 0;
    const bb = prev[i];
    const c = i >= bpp ? prev[i - bpp] : 0;
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
  cur.copy(px, y * stride);
  prev = cur;
}

// --- 统计 ---
let white = 0, red = 0, black = 0, mid = 0, other = 0;
const nearWhite = [], grayHist = new Array(8).fill(0);
for (let i = 0; i < W * H; i++) {
  const r = px[i * 3], g = px[i * 3 + 1], bl = px[i * 3 + 2];
  const isW = r > 245 && g > 245 && bl > 245;
  const isR = r > 200 && g < 70 && bl < 70;
  const isDark = r < 60 && g < 60 && bl < 60;
  if (isW) white++;
  else if (isR) red++;
  else if (isDark) black++;
  else {
    mid++;
    if (r > 200 && Math.abs(r - g) < 12 && Math.abs(g - bl) < 12) nearWhite.push([r, g, bl]);
    // 灰度分档（用于看是否有中性灰过渡）
    const lum = (r * 0.299 + g * 0.587 + bl * 0.114);
    grayHist[Math.min(7, Math.floor(lum / 32))]++;
  }
}
const total = W * H;
const pct = n => (n / total * 100).toFixed(2) + '%';
console.log('尺寸:', W + 'x' + H, '总像素:', total);
console.log('纯白(>245):', white, pct(white));
console.log('纯红(>200,<70):', red, pct(red));
console.log('近黑(<60):', black, pct(black));
console.log('中间过渡:', mid, pct(mid));
console.log('近白但非纯白(>200 且近灰):', nearWhite.length);
const uniq = {};
for (const c of nearWhite.slice(0, 4000)) { const k = c.join(','); uniq[k] = (uniq[k] || 0) + 1; }
console.log('近白像素的高频取值(前 8):', Object.entries(uniq).sort((a, b2) => b2[1] - a[1]).slice(0, 8).map(x => x[0] + '×' + x[1]).join('  '));
console.log('过渡像素亮度分布 [0-31,32-63,...]:', grayHist.join(' '));
// 角点采样
const corners = [[0,0],[W-1,0],[0,H-1],[W-1,H-1],[W>>1,0],[0,H>>1]];
console.log('角/边采样:', corners.map(([x,y]) => '#'+x+','+y+'=('+px[(y*W+x)*3]+','+px[(y*W+x)*3+1]+','+px[(y*W+x)*3+2]+')').join(' '));
