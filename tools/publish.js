// 轮询 GitHub 授权状态，成功后自动创建仓库并上传
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const GH = 'C:/Users/22610/Desktop/App/android-tools/bin/gh.exe';
const PROJECT = 'C:/Users/22610/Desktop/App/sedentary-reminder';
const REPO = 'sedentary-reminder';

function gh(args, input) {
  const r = spawnSync(GH, args, { input: input || '', encoding: 'utf8', timeout: 60000 });
  return { status: r.status, out: (r.stdout || '') + (r.stderr || '') };
}

(async () => {
  // 1. 等待用户在浏览器完成设备码授权（最多 5 分钟）
  let ok = false;
  for (let i = 0; i < 75; i++) {
    await new Promise(r => setTimeout(r, 4000));
    const s = gh(['auth', 'status']);
    if (s.status === 0 && s.out.includes('github.com')) { ok = true; break; }
  }
  if (!ok) { console.log('AUTH_TIMEOUT'); return; }
  const who = gh(['api', 'user', '--jq', '.login']);
  const user = who.out.trim();
  console.log('授权成功，账号:', user);

  // 2. 创建公开仓库
  const desc = '久坐提醒 Android App：定时提醒起身活动，前台服务+系统闹钟双通道，弹窗/响铃/震动可选';
  const cr = gh(['repo', 'create', REPO, '--public', '--description', desc]);
  if (cr.status !== 0 && !cr.out.includes('already exists')) {
    console.log('创建仓库失败:', cr.out.slice(0, 200)); return;
  }
  console.log('仓库已创建:', `https://github.com/${user}/${REPO}`);

  // 3. 通过 Git Data API 上传源码（单个 Initial commit）
  const H = { 'Authorization': 'Bearer ' + gh(['auth', 'token']).out.trim(), 'Accept': 'application/vnd.github+json', 'User-Agent': user };
  const api = (ep, method, body) => fetch(`https://api.github.com/repos/${user}/${REPO}${ep}`, {
    method: method || 'GET', headers: H, body: body ? JSON.stringify(body) : undefined
  }).then(async r => [r.status, await r.json()]);

  // 空仓库需先初始化一个提交，Git Data API 才能用
  const init = await api('/contents/README.md', 'PUT', {
    message: 'init', content: Buffer.from('# 久坐提醒').toString('base64')
  });
  console.log('仓库初始化:', init[0] === 201 ? 'ok' : JSON.stringify(init[1]).slice(0, 120));

  // 收集要发布的文件（排除 out/、build.log）
  const files = [];
  (function walk(dir) {
    for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, f.name);
      const rel = path.relative(PROJECT, full).replaceAll('\\', '/');
      if (f.isDirectory()) {
        if (f.name === 'out') continue;
        walk(full);
      } else {
        if (f.name === 'build.log') continue;
        files.push({ rel, full });
      }
    }
  })(PROJECT);
  console.log('待上传文件数:', files.length);

  const tree = [];
  for (const f of files) {
    const b = await api('/git/blobs', 'POST', {
      content: fs.readFileSync(f.full).toString('base64'), encoding: 'base64'
    });
    if (b[0] !== 201) { console.log('blob 失败:', f.rel, JSON.stringify(b[1]).slice(0, 100)); return; }
    tree.push({ path: f.rel, mode: '100644', type: 'blob', sha: b[1].sha });
  }
  const t = await api('/git/trees', 'POST', { tree });
  if (t[0] !== 201) { console.log('tree 失败:', JSON.stringify(t[1]).slice(0, 150)); return; }
  const c = await api('/git/commits', 'POST', {
    message: 'Initial commit: 久坐提醒 Android App v1.1', tree: t[1].sha
  });
  if (c[0] !== 201) { console.log('commit 失败:', JSON.stringify(c[1]).slice(0, 150)); return; }
  let ref = await api('/git/refs/heads/main', 'PATCH', { sha: c[1].sha, force: true });
  if (ref[0] !== 200) {
    ref = await api('/git/refs', 'POST', { ref: 'refs/heads/main', sha: c[1].sha });
  }
  if (ref[0] !== 200 && ref[0] !== 422) { console.log('ref 失败:', JSON.stringify(ref[1]).slice(0, 150)); return; }
  await api('', 'PATCH', { default_branch: 'main', description: desc, homepage: '' });
  console.log('源码上传完成 ✓');

  // 4. 创建 Release 并上传 APK
  const apk = 'C:/Users/22610/Desktop/久坐提醒.apk';
  const rel = spawnSync(GH, ['release', 'create', 'v1.1', apk,
    '--repo', `${user}/${REPO}`,
    '--title', '久坐提醒 v1.1',
    '--notes', '直接下载 APK 安装（Android 8.0+）。\n\n' +
      '- 前台常驻服务 + 系统闹钟双通道触发，锁屏/后台准点提醒\n' +
      '- 弹窗、响铃、震动均可独立开关\n' +
      '- 更新说明与源码构建方法见 README\n\n' +
      '首次使用请允许「自启动」「省电策略无限制」，详见 README 常见问题。'],
    { encoding: 'utf8', timeout: 120000 });
  if (rel.status !== 0) console.log('release 失败:', (rel.stdout + rel.stderr).slice(0, 200));
  else console.log('Release 创建成功 ✓');

  console.log('DONE https://github.com/' + user + '/' + REPO);
})().catch(e => console.log('ERR', String(e).slice(0, 200)));
