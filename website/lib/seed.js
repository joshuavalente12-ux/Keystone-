const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

// Adds one demo game the first time the site starts, so it isn't empty.
function seedDemo(db, gamesDir) {
  if (db.prepare('SELECT COUNT(*) AS n FROM games').get().n > 0) return;
  let user = db.prepare('SELECT id FROM users WHERE username = ?').get('arcade');
  if (!user) {
    // Random password nobody knows: this account only owns the demo game.
    const salt = crypto.randomBytes(16).toString('hex');
    const hash = crypto.scryptSync(crypto.randomBytes(24).toString('hex'), salt, 64).toString('hex');
    const r = db.prepare('INSERT INTO users (username, pass_hash, bio, created_at) VALUES (?, ?, ?, ?)')
      .run('arcade', `${salt}:${hash}`, 'Demo games that ship with the site.', Date.now());
    user = { id: Number(r.lastInsertRowid) };
  }
  const r = db.prepare(`INSERT INTO games (user_id, title, description, genre, kind, entry, created_at)
    VALUES (?, ?, ?, ?, 'html5', 'index.html', ?)`).run(user.id, 'Star Catcher',
    'Catch the falling stars and dodge the rocks! Use the arrow keys, A/D, or drag on a touch screen.\n\nThis demo shows how a browser game looks on the site. Upload yours with the "Post a game" button.',
    'Arcade', Date.now());
  const site = path.join(gamesDir, String(r.lastInsertRowid), 'site');
  fs.mkdirSync(site, { recursive: true });
  fs.copyFileSync(path.join(__dirname, '..', 'seed', 'star-catcher', 'index.html'), path.join(site, 'index.html'));
}

module.exports = { seedDemo };
