// 下载 awesome-design-md 精选品牌 DESIGN.md 作为参考素材
// 用法: node fetch-design-md.js
const https = require('https');
const fs = require('fs');
const path = require('path');

const OWNER = 'VoltAgent';
const REPO = 'awesome-design-md';
const BRANCH = 'main';
const OUT = path.join(__dirname, 'awesome-design-md');

const targets = [
  'README.md',
  'LICENSE',
  'design-md/stripe/DESIGN.md',
  'design-md/vercel/DESIGN.md',
  'design-md/linear.app/DESIGN.md',
  'design-md/ibm/DESIGN.md',
  'design-md/clickhouse/DESIGN.md',
  'design-md/supabase/DESIGN.md',
  'design-md/posthog/DESIGN.md',
  'design-md/shopify/DESIGN.md',
];

function get(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    https
      .get(url, { timeout: 30000, headers: { 'user-agent': 'node-design-md-fetch' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          if (redirects > 5) return reject(new Error('too many redirects'));
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

(async () => {
  const results = [];
  for (const t of targets) {
    const url = `https://raw.githubusercontent.com/${OWNER}/${REPO}/${BRANCH}/${t}`;
    const dest = path.join(OUT, t.replace(/\//g, path.sep));
    try {
      const buf = await get(url);
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, buf);
      results.push(`OK   ${t} (${buf.length} bytes)`);
    } catch (e) {
      results.push(`FAIL ${t} -> ${e.message}`);
    }
  }
  console.log(results.join('\n'));
  console.log('\nsaved under: ' + OUT);
})();
