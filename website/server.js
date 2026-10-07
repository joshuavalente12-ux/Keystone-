const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const express = require('express');
const multer = require('multer');
const { openDb } = require('./lib/db');
const { extractZip } = require('./lib/unzip');
const { seedDemo } = require('./lib/seed');

const MB = 1024 * 1024;
const SESSION_DAYS = 30;
const GENRES = ['Action', 'Adventure', 'Arcade', 'Puzzle', 'RPG', 'Platformer',
  'Shooter', 'Strategy', 'Racing', 'Sports', 'Simulation', 'Horror', 'Other'];

function createApp({
  dataDir = path.join(__dirname, 'data'),
  maxUploadMb = Number(process.env.MAX_UPLOAD_MB) || 200,
  maxUnzippedMb = Number(process.env.MAX_UNZIPPED_MB) || 300,
  seed = true,
} = {}) {
  const db = openDb(dataDir);
  const gamesDir = path.join(dataDir, 'games');
  const thumbsDir = path.join(dataDir, 'thumbs');
  const tmpDir = path.join(dataDir, 'tmp');
  for (const d of [gamesDir, thumbsDir, tmpDir]) fs.mkdirSync(d, { recursive: true });
  if (seed) seedDemo(db, gamesDir);

  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 'loopback');

  const upload = multer({
    dest: tmpDir,
    limits: { fileSize: maxUploadMb * MB, files: 2, fields: 20 },
  });

  // ---------- helpers ----------
  const hashPassword = (pw) => {
    const salt = crypto.randomBytes(16).toString('hex');
    const hash = crypto.scryptSync(pw, salt, 64).toString('hex');
    return `${salt}:${hash}`;
  };
  const checkPassword = (pw, stored) => {
    const [salt, hash] = stored.split(':');
    const test = crypto.scryptSync(pw, salt, 64);
    return crypto.timingSafeEqual(test, Buffer.from(hash, 'hex'));
  };
  const parseCookies = (header = '') => Object.fromEntries(
    header.split(';').map((c) => c.trim().split('=')).filter(([k, v]) => k && v)
      .map(([k, v]) => [k, decodeURIComponent(v)]),
  );
  const setSession = (req, res, userId) => {
    const token = crypto.randomBytes(32).toString('hex');
    db.prepare('INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)')
      .run(token, userId, Date.now() + SESSION_DAYS * 86400e3);
    res.cookie('sid', token, {
      httpOnly: true, sameSite: 'lax', secure: req.secure,
      maxAge: SESSION_DAYS * 86400e3, path: '/',
    });
  };
  const fail = (res, status, error) => res.status(status).json({ error });
  const requireUser = (req, res, next) => (req.user ? next() : fail(res, 401, 'Please log in first.'));
  const cleanup = (files) => {
    for (const f of Object.values(files || {}).flat()) fs.rm(f.path, { force: true }, () => {});
  };
  const sniffImage = (file) => {
    const fd = fs.openSync(file, 'r');
    const b = Buffer.alloc(12);
    fs.readSync(fd, b, 0, 12, 0);
    fs.closeSync(fd);
    if (b[0] === 0x89 && b.toString('ascii', 1, 4) === 'PNG') return 'png';
    if (b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return 'jpg';
    if (b.toString('ascii', 0, 3) === 'GIF') return 'gif';
    if (b.toString('ascii', 0, 4) === 'RIFF' && b.toString('ascii', 8, 12) === 'WEBP') return 'webp';
    return null;
  };
  const gameRow = (g, userId) => ({
    id: g.id,
    title: g.title,
    description: g.description,
    genre: g.genre,
    kind: g.kind,
    author: g.author,
    authorId: g.user_id,
    thumb: g.thumb ? `/thumbs/${g.thumb}` : null,
    plays: g.plays,
    likes: g.likes,
    comments: g.comments,
    createdAt: g.created_at,
    playUrl: g.kind === 'html5' ? `/play/${g.id}/${g.entry.split('/').map(encodeURIComponent).join('/')}` : null,
    downloadUrl: g.kind === 'download' ? `/download/${g.id}` : null,
    fileName: g.file_name,
    fileSize: g.file_size,
    linkUrl: g.link_url,
    likedByMe: userId ? !!db.prepare('SELECT 1 FROM likes WHERE user_id = ? AND game_id = ?').get(userId, g.id) : false,
  });
  const GAME_SELECT = `
    SELECT g.*, u.username AS author,
      (SELECT COUNT(*) FROM likes l WHERE l.game_id = g.id) AS likes,
      (SELECT COUNT(*) FROM comments c WHERE c.game_id = g.id) AS comments
    FROM games g JOIN users u ON u.id = g.user_id`;

  // ---------- global middleware ----------
  app.use((req, res, next) => {
    res.set('X-Content-Type-Options', 'nosniff');
    res.set('Referrer-Policy', 'strict-origin-when-cross-origin');
    const token = parseCookies(req.headers.cookie).sid;
    if (token) {
      const row = db.prepare(`SELECT u.id, u.username, u.bio FROM sessions s JOIN users u ON u.id = s.user_id
        WHERE s.token = ? AND s.expires_at > ?`).get(token, Date.now());
      if (row) req.user = row;
    }
    next();
  });

  // Mutating API calls must carry a custom header. Browsers won't let other
  // sites (or sandboxed game iframes) send it, which blocks CSRF.
  app.use('/api', (req, res, next) => {
    if (req.method !== 'GET' && req.get('X-Requested-With') !== 'arcade') {
      return fail(res, 403, 'Missing request header.');
    }
    next();
  });
  app.use('/api', express.json({ limit: '50kb' }));

  // ---------- auth ----------
  app.get('/api/me', (req, res) => res.json({ user: req.user || null }));

  app.post('/api/signup', (req, res) => {
    const username = String(req.body?.username || '').trim();
    const password = String(req.body?.password || '');
    if (!/^[A-Za-z0-9_-]{3,20}$/.test(username)) {
      return fail(res, 400, 'Username must be 3-20 letters, numbers, _ or -.');
    }
    if (password.length < 8) return fail(res, 400, 'Password must be at least 8 characters.');
    if (db.prepare('SELECT 1 FROM users WHERE username = ?').get(username)) {
      return fail(res, 409, 'That username is taken.');
    }
    const { lastInsertRowid } = db.prepare('INSERT INTO users (username, pass_hash, created_at) VALUES (?, ?, ?)')
      .run(username, hashPassword(password), Date.now());
    setSession(req, res, Number(lastInsertRowid));
    res.json({ user: { id: Number(lastInsertRowid), username, bio: '' } });
  });

  app.post('/api/login', (req, res) => {
    const username = String(req.body?.username || '').trim();
    const password = String(req.body?.password || '');
    const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (!user || !checkPassword(password, user.pass_hash)) {
      return fail(res, 401, 'Wrong username or password.');
    }
    setSession(req, res, user.id);
    res.json({ user: { id: user.id, username: user.username, bio: user.bio } });
  });

  app.post('/api/logout', (req, res) => {
    const token = parseCookies(req.headers.cookie).sid;
    if (token) db.prepare('DELETE FROM sessions WHERE token = ?').run(token);
    res.clearCookie('sid', { path: '/' });
    res.json({ ok: true });
  });

  // ---------- games ----------
  app.get('/api/genres', (req, res) => res.json({ genres: GENRES }));

  app.get('/api/games', (req, res) => {
    const where = [];
    const args = [];
    const q = String(req.query.q || '').trim();
    if (q) {
      where.push('(g.title LIKE ? OR g.description LIKE ? OR u.username LIKE ?)');
      const like = `%${q.replace(/[%_]/g, '')}%`;
      args.push(like, like, like);
    }
    if (GENRES.includes(req.query.genre)) { where.push('g.genre = ?'); args.push(req.query.genre); }
    if (['html5', 'download', 'link'].includes(req.query.kind)) { where.push('g.kind = ?'); args.push(req.query.kind); }
    if (req.query.user) { where.push('u.username = ?'); args.push(String(req.query.user)); }
    const order = { popular: 'g.plays DESC', liked: 'likes DESC', new: 'g.created_at DESC' }[req.query.sort] || 'g.created_at DESC';
    const limit = Math.min(Number(req.query.limit) || 48, 100);
    const offset = Math.max(Number(req.query.offset) || 0, 0);
    const rows = db.prepare(`${GAME_SELECT} ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
      ORDER BY ${order}, g.id DESC LIMIT ? OFFSET ?`).all(...args, limit, offset);
    res.json({ games: rows.map((g) => gameRow(g, req.user?.id)) });
  });

  app.get('/api/games/:id', (req, res) => {
    const g = db.prepare(`${GAME_SELECT} WHERE g.id = ?`).get(Number(req.params.id));
    if (!g) return fail(res, 404, 'Game not found.');
    res.json({ game: gameRow(g, req.user?.id) });
  });

  app.post('/api/games', requireUser,
    upload.fields([{ name: 'file', maxCount: 1 }, { name: 'thumb', maxCount: 1 }]),
    (req, res) => {
      const files = req.files || {};
      const body = req.body || {};
      const title = String(body.title || '').trim().slice(0, 80);
      const description = String(body.description || '').trim().slice(0, 4000);
      const genre = GENRES.includes(body.genre) ? body.genre : 'Other';
      const kind = String(body.kind || '');
      const gameFile = files.file?.[0];
      const thumbFile = files.thumb?.[0];
      let gameId = null;
      try {
        if (!title) throw new Error('Give your game a title.');
        if (!['html5', 'download', 'link'].includes(kind)) throw new Error('Choose how people get your game.');
        let linkUrl = null;
        if (kind === 'link') {
          try { linkUrl = new URL(String(body.linkUrl || '')); } catch { /* handled below */ }
          if (!linkUrl || !['http:', 'https:'].includes(linkUrl.protocol)) {
            throw new Error('Enter a full link starting with https://');
          }
          linkUrl = linkUrl.href;
        } else if (!gameFile) {
          throw new Error('Choose a file to upload.');
        }
        let thumbExt = null;
        if (thumbFile) {
          thumbExt = sniffImage(thumbFile.path);
          if (!thumbExt) throw new Error('Cover image must be PNG, JPG, GIF or WebP.');
          if (thumbFile.size > 5 * MB) throw new Error('Cover image must be under 5 MB.');
        }

        const { lastInsertRowid } = db.prepare(`INSERT INTO games
          (user_id, title, description, genre, kind, link_url, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`)
          .run(req.user.id, title, description, genre, kind, linkUrl, Date.now());
        gameId = Number(lastInsertRowid);
        const dir = path.join(gamesDir, String(gameId));
        fs.mkdirSync(dir, { recursive: true });

        if (kind === 'html5') {
          const site = path.join(dir, 'site');
          fs.mkdirSync(site);
          const lower = gameFile.originalname.toLowerCase();
          let entry;
          if (lower.endsWith('.html') || lower.endsWith('.htm')) {
            fs.renameSync(gameFile.path, path.join(site, 'index.html'));
            entry = 'index.html';
          } else if (lower.endsWith('.zip')) {
            const written = extractZip(fs.readFileSync(gameFile.path), site, { maxTotalBytes: maxUnzippedMb * MB });
            const htmls = written.filter((p) => /(^|\/)index\.html?$/i.test(p))
              .sort((a, b) => a.split('/').length - b.split('/').length);
            if (!htmls.length) throw new Error('Your zip needs an index.html file (the page that starts the game).');
            entry = htmls[0];
          } else {
            throw new Error('Browser games must be a .zip (with index.html inside) or a single .html file.');
          }
          db.prepare('UPDATE games SET entry = ? WHERE id = ?').run(entry, gameId);
        } else if (kind === 'download') {
          const name = path.basename(gameFile.originalname).replace(/[^\w.\- ()]/g, '_').slice(0, 120) || 'game';
          fs.renameSync(gameFile.path, path.join(dir, 'download.bin'));
          db.prepare('UPDATE games SET file_name = ?, file_size = ? WHERE id = ?').run(name, gameFile.size, gameId);
        }

        if (thumbFile) {
          const thumbName = `${gameId}-${crypto.randomBytes(4).toString('hex')}.${thumbExt}`;
          fs.renameSync(thumbFile.path, path.join(thumbsDir, thumbName));
          db.prepare('UPDATE games SET thumb = ? WHERE id = ?').run(thumbName, gameId);
        }
        cleanup(files);
        res.status(201).json({ id: gameId });
      } catch (err) {
        cleanup(files);
        if (gameId) {
          db.prepare('DELETE FROM games WHERE id = ?').run(gameId);
          fs.rmSync(path.join(gamesDir, String(gameId)), { recursive: true, force: true });
        }
        fail(res, 400, err.message);
      }
    });

  app.patch('/api/games/:id', requireUser, (req, res) => {
    const g = db.prepare('SELECT * FROM games WHERE id = ?').get(Number(req.params.id));
    if (!g) return fail(res, 404, 'Game not found.');
    if (g.user_id !== req.user.id) return fail(res, 403, 'You can only edit your own games.');
    const title = String(req.body?.title ?? g.title).trim().slice(0, 80);
    if (!title) return fail(res, 400, 'Title cannot be empty.');
    const description = String(req.body?.description ?? g.description).trim().slice(0, 4000);
    const genre = GENRES.includes(req.body?.genre) ? req.body.genre : g.genre;
    db.prepare('UPDATE games SET title = ?, description = ?, genre = ? WHERE id = ?').run(title, description, genre, g.id);
    res.json({ ok: true });
  });

  app.delete('/api/games/:id', requireUser, (req, res) => {
    const g = db.prepare('SELECT * FROM games WHERE id = ?').get(Number(req.params.id));
    if (!g) return fail(res, 404, 'Game not found.');
    if (g.user_id !== req.user.id) return fail(res, 403, 'You can only delete your own games.');
    db.prepare('DELETE FROM games WHERE id = ?').run(g.id);
    fs.rmSync(path.join(gamesDir, String(g.id)), { recursive: true, force: true });
    if (g.thumb) fs.rmSync(path.join(thumbsDir, g.thumb), { force: true });
    res.json({ ok: true });
  });

  app.post('/api/games/:id/play', (req, res) => {
    db.prepare('UPDATE games SET plays = plays + 1 WHERE id = ?').run(Number(req.params.id));
    res.json({ ok: true });
  });

  app.post('/api/games/:id/like', requireUser, (req, res) => {
    const id = Number(req.params.id);
    if (!db.prepare('SELECT 1 FROM games WHERE id = ?').get(id)) return fail(res, 404, 'Game not found.');
    const had = db.prepare('DELETE FROM likes WHERE user_id = ? AND game_id = ?').run(req.user.id, id).changes;
    if (!had) db.prepare('INSERT INTO likes (user_id, game_id) VALUES (?, ?)').run(req.user.id, id);
    const { n } = db.prepare('SELECT COUNT(*) AS n FROM likes WHERE game_id = ?').get(id);
    res.json({ liked: !had, likes: n });
  });

  app.get('/api/games/:id/comments', (req, res) => {
    const rows = db.prepare(`SELECT c.id, c.body, c.created_at AS createdAt, u.username AS author, c.user_id AS authorId
      FROM comments c JOIN users u ON u.id = c.user_id WHERE c.game_id = ? ORDER BY c.id DESC LIMIT 200`)
      .all(Number(req.params.id));
    res.json({ comments: rows });
  });

  app.post('/api/games/:id/comments', requireUser, (req, res) => {
    const id = Number(req.params.id);
    const body = String(req.body?.body || '').trim().slice(0, 1000);
    if (!body) return fail(res, 400, 'Write something first.');
    if (!db.prepare('SELECT 1 FROM games WHERE id = ?').get(id)) return fail(res, 404, 'Game not found.');
    db.prepare('INSERT INTO comments (game_id, user_id, body, created_at) VALUES (?, ?, ?, ?)')
      .run(id, req.user.id, body, Date.now());
    res.status(201).json({ ok: true });
  });

  app.delete('/api/comments/:id', requireUser, (req, res) => {
    const c = db.prepare(`SELECT c.*, g.user_id AS game_owner FROM comments c JOIN games g ON g.id = c.game_id
      WHERE c.id = ?`).get(Number(req.params.id));
    if (!c) return fail(res, 404, 'Comment not found.');
    if (c.user_id !== req.user.id && c.game_owner !== req.user.id) return fail(res, 403, 'Not allowed.');
    db.prepare('DELETE FROM comments WHERE id = ?').run(c.id);
    res.json({ ok: true });
  });

  // ---------- users ----------
  app.get('/api/users/:name', (req, res) => {
    const u = db.prepare('SELECT id, username, bio, created_at AS createdAt FROM users WHERE username = ?')
      .get(String(req.params.name));
    if (!u) return fail(res, 404, 'User not found.');
    res.json({ user: u });
  });

  app.patch('/api/me', requireUser, (req, res) => {
    const bio = String(req.body?.bio || '').trim().slice(0, 500);
    db.prepare('UPDATE users SET bio = ? WHERE id = ?').run(bio, req.user.id);
    res.json({ ok: true });
  });

  app.use('/api', (req, res) => fail(res, 404, 'Not found.'));

  // ---------- game files ----------
  // Uploaded games run with a sandboxed, opaque origin so their scripts can
  // never act as the logged-in user on this site.
  app.get('/play/:id/*file', (req, res) => {
    const g = db.prepare("SELECT id FROM games WHERE id = ? AND kind = 'html5'").get(Number(req.params.id));
    if (!g) return res.status(404).send('Game not found');
    res.set({
      'Content-Security-Policy': 'sandbox allow-scripts allow-pointer-lock allow-popups allow-forms allow-modals allow-downloads',
      'Access-Control-Allow-Origin': '*',
      'Cross-Origin-Resource-Policy': 'cross-origin',
    });
    const rel = req.params.file.join('/');
    res.sendFile(rel, { root: path.join(gamesDir, String(g.id), 'site'), dotfiles: 'deny' }, (err) => {
      if (err && !res.headersSent) res.status(err.status || 404).send('Not found');
    });
  });

  app.get('/download/:id', (req, res) => {
    const g = db.prepare("SELECT id, file_name FROM games WHERE id = ? AND kind = 'download'").get(Number(req.params.id));
    if (!g) return res.status(404).send('Not found');
    db.prepare('UPDATE games SET plays = plays + 1 WHERE id = ?').run(g.id);
    res.download(path.join(gamesDir, String(g.id), 'download.bin'), g.file_name);
  });

  app.use('/thumbs', express.static(thumbsDir, { maxAge: '7d', index: false }));
  app.use(express.static(path.join(__dirname, 'public'), { index: 'index.html' }));

  app.use((err, req, res, next) => { // eslint-disable-line no-unused-vars
    cleanup(req.files);
    if (err instanceof multer.MulterError) {
      return fail(res, 400, err.code === 'LIMIT_FILE_SIZE' ? `Files must be under ${maxUploadMb} MB.` : err.message);
    }
    console.error(err);
    fail(res, 500, 'Something went wrong.');
  });

  return app;
}

if (require.main === module) {
  const port = Number(process.env.PORT) || 3000;
  createApp({ dataDir: process.env.DATA_DIR || path.join(__dirname, 'data') })
    .listen(port, () => console.log(`Keystone Arcade running at http://localhost:${port}`));
}

module.exports = { createApp };
