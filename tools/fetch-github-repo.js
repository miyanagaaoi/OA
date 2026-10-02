// 下载 GitHub 仓库 tarball 并解包到指定目录（无 git 环境下的替代方案）
// 用法: node fetch-github-repo.js <owner/repo> <ref> <destDir>
const https = require('https');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const [slug, ref, dest] = process.argv.slice(2);
if (!slug || !ref || !dest) {
  console.error('usage: node fetch-github-repo.js <owner/repo> <ref> <destDir>');
  process.exit(2);
}

function get(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    https
      .get(url, { timeout: 120000, headers: { 'user-agent': 'node', accept: '*/*' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          if (redirects > 6) return reject(new Error('too many redirects'));
          return resolve(get(res.headers.location, redirects + 1));
        }
        if (res.statusCode !== 200) {
          res.resume();
          return reject(new Error('HTTP ' + res.statusCode + ' for ' + url));
        }
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => resolve(Buffer.concat(chunks)));
      })
      .on('error', reject)
      .on('timeout', function () {
        this.destroy(new Error('timeout ' + url));
      });
  });
}

// 极简 tar 解析：仅支持 ustar/GNU 常规文件与目录
function untar(buf, destDir) {
  const files = [];
  let off = 0;
  while (off + 512 <= buf.length) {
    const header = buf.subarray(off, off + 512);
    if (header.every((b) => b === 0)) break;
    const readStr = (start, len) => header.subarray(start, start + len).toString('utf8').replace(/\0.*$/, '').trim();
    const name = readStr(0, 100);
    const prefix = readStr(345, 155);
    const size = parseInt(readStr(124, 12), 8) || 0;
    const type = String.fromCharCode(header[156]);
    const fullName = prefix ? prefix + '/' + name : name;
    off += 512;
    const data = buf.subarray(off, off + size);
    off += Math.ceil(size / 512) * 512;
    if (type === '0' || type === '\0' || type === '') {
      // 去掉归档顶层目录
      const rel = fullName.split('/').slice(1).join('/');
      if (!rel || rel.includes('..')) continue;
      const target = path.join(destDir, rel.replace(/\//g, path.sep));
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.writeFileSync(target, data);
      files.push(rel);
    }
  }
  return files;
}

(async () => {
  const url = `https://codeload.github.com/${slug}/tar.gz/${ref}`;
  const gz = await get(url);
  const tar = zlib.gunzipSync(gz);
  fs.mkdirSync(dest, { recursive: true });
  const files = untar(tar, dest);
  console.log(`downloaded ${slug}@${ref}: ${gz.length} bytes gz, ${files.length} files extracted to ${dest}`);
  console.log('\ntop-level entries:');
  const tops = new Set(files.map((f) => f.split('/')[0]));
  for (const t of [...tops].sort()) console.log('  ' + t);
})();
