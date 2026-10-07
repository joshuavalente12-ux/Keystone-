const { test, before, after } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const { createApp } = require('../server');

// Build a zip in memory: files = [{ name, data, store? }]
function makeZip(files) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const f of files) {
    const data = Buffer.from(f.data);
    const comp = f.store ? data : zlib.deflateRawSync(data);
    const name = Buffer.from(f.name);
    const crc = zlib.crc32(data);
    const loc = Buffer.alloc(30);
    loc.writeUInt32LE(0x04034b50, 0); loc.writeUInt16LE(20, 4); loc.writeUInt16LE(f.store ? 0 : 8, 8);
    loc.writeUInt32LE(crc, 14); loc.writeUInt32LE(comp.length, 18); loc.writeUInt32LE(data.length, 22);
    loc.writeUInt16LE(name.length, 26);
    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0); cen.writeUInt16LE(20, 4); cen.writeUInt16LE(20, 6);
    cen.writeUInt16LE(f.store ? 0 : 8, 10); cen.writeUInt32LE(crc, 16); cen.writeUInt32LE(comp.length, 20);
    cen.writeUInt32LE(data.length, 24); cen.writeUInt16LE(name.length, 28); cen.writeUInt32LE(offset, 42);
    locals.push(loc, name, comp);
    centrals.push(cen, name);
    offset += 30 + name.length + comp.length;
  }
  const cenBuf = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(files.length, 8); end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(cenBuf.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, cenBuf, end]);
}

let server; let base; let dataDir;
before(async () => {
  dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'arcade-test-'));
  const app = createApp({ dataDir, maxUnzippedMb: 1 });
  await new Promise((r) => { server = app.listen(0, r); });
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => { server.close(); fs.rmSync(dataDir, { recursive: true, force: true }); });

const H = { 'X-Requested-With': 'arcade' };
async function signup(username) {
  const res = await fetch(`${base}/api/signup`, {
    method: 'POST', headers: { ...H, 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: 'password123' }),
  });
  assert.equal(res.status, 200);
  return res.headers.get('set-cookie').split(';')[0];
}
function post(cookie, fields, files = {}) {
  const fd = new FormData();
  for (const [k, v] of Object.entries(fields)) fd.append(k, v);
  for (const [k, [buf, name]] of Object.entries(files)) fd.append(k, new Blob([buf]), name);
  return fetch(`${base}/api/games`, { method: 'POST', headers: { ...H, cookie }, body: fd });
}

test('demo game is seeded', async () => {
  const { games } = await (await fetch(`${base}/api/games`)).json();
  assert.equal(games[0].title, 'Star Catcher');
  const page = await fetch(`${base}${games[0].playUrl}`);
  assert.equal(page.status, 200);
  assert.match(page.headers.get('content-security-policy'), /^sandbox allow-scripts/);
});

test('mutating API calls need the custom header', async () => {
  const res = await fetch(`${base}/api/signup`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'nohdr', password: 'password123' }),
  });
  assert.equal(res.status, 403);
});

test('posting requires login', async () => {
  const res = await post('', { title: 'x', kind: 'link', linkUrl: 'https://example.com' });
  assert.equal(res.status, 401);
});

test('upload a zipped browser game and play it', async () => {
  const cookie = await signup('maker');
  const zip = makeZip([
    { name: 'build/index.html', data: '<h1>hi</h1>' },
    { name: 'build/js/game.js', data: 'console.log(1)', store: true },
  ]);
  const res = await post(cookie, { title: 'Zip Game', kind: 'html5', genre: 'Puzzle' }, { file: [zip, 'game.zip'] });
  assert.equal(res.status, 201, await res.clone().text());
  const { id } = await res.json();
  const { game } = await (await fetch(`${base}/api/games/${id}`)).json();
  assert.equal(game.playUrl, `/play/${id}/build/index.html`);
  assert.equal(game.genre, 'Puzzle');
  assert.equal(await (await fetch(`${base}/play/${id}/build/js/game.js`)).text(), 'console.log(1)');
  assert.equal((await fetch(`${base}/play/${id}/../../arcade.db`)).status, 404);
});

test('zip path traversal is rejected and cleaned up', async () => {
  const cookie = await signup('sneaky');
  const zip = makeZip([{ name: 'index.html', data: 'x' }, { name: '../../evil.txt', data: 'x' }]);
  const res = await post(cookie, { title: 'Evil', kind: 'html5' }, { file: [zip, 'evil.zip'] });
  assert.equal(res.status, 400);
  assert.match((await res.json()).error, /Unsafe path/);
  assert.ok(!fs.existsSync(path.join(dataDir, 'evil.txt')));
  const { games } = await (await fetch(`${base}/api/games?q=Evil`)).json();
  assert.equal(games.length, 0);
});

test('zip bombs are stopped by the unzipped size cap', async () => {
  const cookie = await signup('bomber');
  const zip = makeZip([{ name: 'index.html', data: Buffer.alloc(3 * 1024 * 1024) }]);
  const res = await post(cookie, { title: 'Bomb', kind: 'html5' }, { file: [zip, 'b.zip'] });
  assert.equal(res.status, 400);
  assert.match((await res.json()).error, /too large/);
});

test('downloadable game, likes, comments and ownership', async () => {
  const owner = await signup('apkdev');
  const other = await signup('player1');
  const res = await post(owner, { title: 'My APK', kind: 'download' }, { file: [Buffer.from('APKDATA'), 'my game.apk'] });
  assert.equal(res.status, 201);
  const { id } = await res.json();

  const dl = await fetch(`${base}/download/${id}`);
  assert.equal(await dl.text(), 'APKDATA');
  assert.match(dl.headers.get('content-disposition'), /attachment; filename="my game.apk"/);

  const like = await (await fetch(`${base}/api/games/${id}/like`, { method: 'POST', headers: { ...H, cookie: other } })).json();
  assert.deepEqual(like, { liked: true, likes: 1 });

  const c = await fetch(`${base}/api/games/${id}/comments`, {
    method: 'POST', headers: { ...H, cookie: other, 'Content-Type': 'application/json' },
    body: JSON.stringify({ body: 'Fun!' }),
  });
  assert.equal(c.status, 201);

  const del = await fetch(`${base}/api/games/${id}`, { method: 'DELETE', headers: { ...H, cookie: other } });
  assert.equal(del.status, 403);
  const del2 = await fetch(`${base}/api/games/${id}`, { method: 'DELETE', headers: { ...H, cookie: owner } });
  assert.equal(del2.status, 200);
  assert.equal((await fetch(`${base}/download/${id}`)).status, 404);
});

test('cover images must be real images', async () => {
  const cookie = await signup('artist');
  const res = await post(cookie, { title: 'Bad Cover', kind: 'link', linkUrl: 'https://example.com' },
    { thumb: [Buffer.from('<svg onload=alert(1)>'), 'x.png'] });
  assert.equal(res.status, 400);
  const bad = await post(cookie, { title: 'Bad link', kind: 'link', linkUrl: 'javascript:alert(1)' });
  assert.equal(bad.status, 400);
});
