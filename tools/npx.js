// 通过 node 直接调用 npm 的 npx-cli，绕开本机 "禁止运行脚本" 的执行策略限制
// 用法: node tools/npx.js -y @google/design.md lint DESIGN.md
const { spawnSync } = require('child_process');
const path = require('path');

const npxCli = path.join(path.dirname(process.execPath), 'node_modules', 'npm', 'bin', 'npx-cli.js');
const r = spawnSync(process.execPath, [npxCli, ...process.argv.slice(2)], {
  stdio: 'inherit',
  env: {
    ...process.env,
    npm_config_cache: path.join(__dirname, '..', '.npm-cache'),
    npm_config_update_notifier: 'false',
  },
});
process.exit(r.status === null ? 1 : r.status);
