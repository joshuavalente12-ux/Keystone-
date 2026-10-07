'use strict';

// ---------- tiny DOM helper (all user text goes through textContent) ----------
function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else if (k === 'style') el.style.cssText = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

const view = document.getElementById('view');
const nav = document.getElementById('nav');
let me = null;
let genres = [];

async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch(`/api${path}`, {
    method,
    headers: { 'X-Requested-With': 'arcade', ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove('show'), 2600);
}

const hue = (s) => {
  let x = 2166136261;
  for (const c of String(s)) x = Math.imul(x ^ c.charCodeAt(0), 16777619);
  return (x >>> 0) % 360;
};
const gradient = (s) => `background:linear-gradient(135deg,hsl(${hue(s)} 70% 55%),hsl(${(hue(s) + 60) % 360} 65% 35%))`;
const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;
const kindLabel = { html5: 'Play in browser', download: 'Download', link: 'External link' };
function ago(ts) {
  const s = (Date.now() - ts) / 1000;
  for (const [n, u] of [[31536000, 'year'], [2592000, 'month'], [86400, 'day'], [3600, 'hour'], [60, 'minute']]) {
    if (s >= n) return `${plural(Math.floor(s / n), u)} ago`;
  }
  return 'just now';
}
function size(bytes) {
  if (!bytes) return '';
  const u = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  while (bytes >= 1024 && i < u.length - 1) { bytes /= 1024; i++; }
  return `${bytes.toFixed(i ? 1 : 0)} ${u[i]}`;
}

function thumb(game) {
  if (game.thumb) return h('img', { class: 'thumb', src: game.thumb, alt: '', loading: 'lazy' });
  return h('div', { class: 'thumb placeholder', style: gradient(game.title), 'aria-hidden': 'true' },
    game.title.trim().charAt(0).toUpperCase() || '?');
}

function gameCard(g) {
  return h('a', { class: 'card', href: `#/game/${g.id}` },
    thumb(g),
    h('div', { class: 'card-body' },
      h('div', { class: 'card-title' }, g.title),
      h('div', { class: 'meta' }, `by ${g.author}`),
      h('div', { class: 'meta' },
        h('span', { class: `badge ${g.kind}` }, kindLabel[g.kind]),
        h('span', null, `▶ ${g.plays}`),
        h('span', null, `♥ ${g.likes}`))));
}

function renderNav() {
  nav.replaceChildren(
    h('a', { class: 'btn primary', href: me ? '#/upload' : '#/login?next=upload' }, '+ Post a game'),
    ...(me
      ? [h('a', { class: 'btn ghost', href: `#/user/${encodeURIComponent(me.username)}` }, me.username),
        h('button', { class: 'btn ghost', onclick: logout }, 'Log out')]
      : [h('a', { class: 'btn ghost', href: '#/login' }, 'Log in')]));
}

async function logout() {
  await api('/logout', { method: 'POST' });
  me = null;
  renderNav();
  location.hash = '#/';
  toast('Logged out');
}

// ---------- pages ----------
async function homePage(params) {
  const q = params.get('q') || '';
  const sort = params.get('sort') || 'new';
  const kind = params.get('kind') || '';
  const genre = params.get('genre') || '';
  document.querySelector('#search input').value = q;

  const setParam = (k, v) => {
    const p = new URLSearchParams(params);
    if (v) p.set(k, v); else p.delete(k);
    location.hash = `#/?${p}`;
  };
  const chip = (label, k, v, cur) => h('button', {
    class: `chip${cur === v ? ' active' : ''}`, onclick: () => setParam(k, v),
  }, label);

  const grid = h('div', { class: 'grid' });
  view.replaceChildren(
    q ? h('h1', null, `Results for "${q}"`) : h('section', { class: 'hero' },
      h('div', null,
        h('h1', null, 'Play games made by people like you'),
        h('p', null, 'Browse browser games, downloads and projects from creators. Made something? Post it and let others play.')),
      h('a', { class: 'btn primary', href: me ? '#/upload' : '#/login?next=upload' }, '+ Post your game')),
    h('div', { class: 'filters' },
      chip('Newest', 'sort', 'new', sort),
      chip('Most played', 'sort', 'popular', sort),
      chip('Most liked', 'sort', 'liked', sort),
      h('span', { class: 'spacer' }),
      h('select', { 'aria-label': 'Type', onchange: (e) => setParam('kind', e.target.value) },
        h('option', { value: '' }, 'All types'),
        ...Object.entries(kindLabel).map(([v, l]) => h('option', { value: v, selected: kind === v }, l))),
      h('select', { 'aria-label': 'Genre', onchange: (e) => setParam('genre', e.target.value) },
        h('option', { value: '' }, 'All genres'),
        ...genres.map((g) => h('option', { value: g, selected: genre === g }, g)))),
    grid);

  const { games } = await api(`/games?${new URLSearchParams({ q, sort, kind, genre })}`);
  grid.replaceWith(games.length ? h('div', { class: 'grid' }, games.map(gameCard))
    : h('div', { class: 'empty' }, q || kind || genre ? 'No games match that search.' : 'No games yet — be the first to post one!'));
}

async function gamePage(id) {
  const { game: g } = await api(`/games/${id}`);
  document.title = `${g.title} · Keystone Arcade`;
  const mine = me && me.id === g.authorId;

  let stage;
  if (g.kind === 'html5') {
    const player = h('div', { class: 'player' });
    const start = h('button', {
      class: 'start', style: g.thumb ? `background-image:url("${g.thumb}")` : gradient(g.title),
      onclick: () => {
        player.replaceChildren(h('iframe', {
          src: g.playUrl, title: g.title,
          sandbox: 'allow-scripts allow-pointer-lock allow-popups allow-forms allow-modals allow-downloads',
          allow: 'fullscreen; gamepad; autoplay',
          allowfullscreen: true,
        }));
        player.querySelector('iframe').focus();
        api(`/games/${g.id}/play`, { method: 'POST' }).catch(() => {});
      },
    }, h('span', null, '▶ Play'));
    player.append(start);
    stage = h('div', null, player,
      h('div', { class: 'player-bar' },
        h('button', { class: 'btn', onclick: () => player.requestFullscreen?.() }, '⛶ Fullscreen'),
        h('a', { class: 'btn ghost', href: g.playUrl, target: '_blank', rel: 'noopener' }, 'Open in new tab')));
  } else {
    const cover = thumb(g);
    cover.style.height = '100%';
    stage = h('div', { class: 'player' }, cover);
  }

  const likeBtn = h('button', {
    class: `btn${g.likedByMe ? ' on' : ''}`,
    onclick: async () => {
      if (!me) { location.hash = `#/login?next=game/${g.id}`; return; }
      const r = await api(`/games/${g.id}/like`, { method: 'POST' });
      likeBtn.classList.toggle('on', r.liked);
      likeBtn.textContent = `♥ ${r.likes}`;
    },
  }, `♥ ${g.likes}`);

  const getIt = g.kind === 'download'
    ? h('div', { class: 'panel download-box' },
      h('h3', null, 'Download'),
      h('div', { class: 'meta' }, `${g.fileName} · ${size(g.fileSize)}`),
      h('a', { class: 'btn primary', href: g.downloadUrl }, '⬇ Download'),
      /\.apk$/i.test(g.fileName || '') ? h('div', { class: 'meta' }, 'Android app: open the file on your phone to install it.') : null)
    : g.kind === 'link'
      ? h('div', { class: 'panel download-box' },
        h('h3', null, 'Play'),
        h('div', { class: 'meta', style: 'overflow-wrap:anywhere' }, new URL(g.linkUrl).host),
        h('a', {
          class: 'btn primary', href: g.linkUrl, target: '_blank', rel: 'noopener noreferrer nofollow',
          onclick: () => api(`/games/${g.id}/play`, { method: 'POST' }).catch(() => {}),
        }, 'Go to game ↗'))
      : null;

  const commentList = h('div', { class: 'comments' });
  const loadComments = async () => {
    const { comments } = await api(`/games/${g.id}/comments`);
    commentList.replaceChildren(...(comments.length ? comments.map((c) => h('div', { class: 'comment' },
      h('header', null,
        h('span', null, h('a', { href: `#/user/${encodeURIComponent(c.author)}` }, c.author), ` · ${ago(c.createdAt)}`),
        me && (me.id === c.authorId || mine) ? h('button', {
          class: 'del',
          onclick: async () => { await api(`/comments/${c.id}`, { method: 'DELETE' }); loadComments(); },
        }, 'Delete') : null),
      h('p', null, c.body))) : [h('div', { class: 'meta' }, 'No comments yet.')]));
  };
  const commentForm = me
    ? h('form', {
      class: 'field',
      onsubmit: async (e) => {
        e.preventDefault();
        const ta = e.target.elements.body;
        try {
          await api(`/games/${g.id}/comments`, { method: 'POST', body: { body: ta.value } });
          ta.value = '';
          loadComments();
        } catch (err) { toast(err.message); }
      },
    },
    h('textarea', { name: 'body', placeholder: 'Say something nice or give feedback…', maxlength: 1000, required: true, style: 'min-height:80px' }),
    h('div', null, h('button', { class: 'btn primary', type: 'submit' }, 'Post comment')))
    : h('div', { class: 'meta' }, h('a', { href: `#/login?next=game/${g.id}` }, 'Log in'), ' to comment.');

  view.replaceChildren(h('div', { class: 'game-layout' },
    h('div', null,
      stage,
      h('h1', { class: 'game-title' }, g.title),
      h('div', { class: 'meta' },
        h('span', null, 'by ', h('a', { href: `#/user/${encodeURIComponent(g.author)}` }, g.author)),
        h('span', null, ago(g.createdAt)),
        h('span', { class: 'badge' }, g.genre),
        h('span', { class: `badge ${g.kind}` }, kindLabel[g.kind])),
      h('div', { class: 'player-bar' }, likeBtn,
        h('button', {
          class: 'btn ghost',
          onclick: () => navigator.clipboard.writeText(location.href).then(() => toast('Link copied')),
        }, '🔗 Share')),
      g.description ? h('p', { class: 'desc' }, g.description) : null,
      h('div', { class: 'panel', style: 'margin-top:24px' },
        h('h3', null, 'Comments'), commentForm, commentList)),
    h('aside', null,
      getIt,
      h('div', { class: 'panel' },
        h('div', { class: 'stats' },
          h('div', null, h('b', null, g.plays), h('span', null, g.kind === 'download' ? 'downloads' : 'plays')),
          h('div', null, h('b', null, g.likes), h('span', null, 'likes')),
          h('div', null, h('b', null, g.comments), h('span', null, 'comments')))),
      mine ? h('div', { class: 'panel' },
        h('h3', null, 'Your game'),
        h('div', { class: 'player-bar', style: 'margin:0' },
          h('a', { class: 'btn', href: `#/edit/${g.id}` }, 'Edit details'),
          h('button', {
            class: 'btn danger',
            onclick: async () => {
              if (!confirm(`Delete "${g.title}"? This can't be undone.`)) return;
              await api(`/games/${g.id}`, { method: 'DELETE' });
              toast('Game deleted');
              location.hash = `#/user/${encodeURIComponent(me.username)}`;
            },
          }, 'Delete'))) : null)));
  loadComments();
}

function field(label, input, hint) {
  return h('div', { class: 'field' }, h('label', { for: input.id }, label), input, hint ? h('div', { class: 'hint' }, hint) : null);
}

function authPage(mode, params) {
  if (me) { location.hash = '#/'; return; }
  const isLogin = mode === 'login';
  const next = params.get('next');
  const err = h('div', { class: 'error' });
  view.replaceChildren(h('form', {
    class: 'form narrow',
    onsubmit: async (e) => {
      e.preventDefault();
      err.textContent = '';
      const f = e.target.elements;
      try {
        const r = await api(isLogin ? '/login' : '/signup', { method: 'POST', body: { username: f.username.value, password: f.password.value } });
        me = r.user;
        renderNav();
        toast(isLogin ? `Welcome back, ${me.username}!` : `Welcome, ${me.username}!`);
        location.hash = `#/${next || ''}`;
      } catch (ex) { err.textContent = ex.message; }
    },
  },
  h('h1', null, isLogin ? 'Log in' : 'Create an account'),
  h('p', { class: 'meta', style: 'margin:0' }, isLogin ? 'Log in to post games, like and comment.' : 'Free, and all you need is a username.'),
  field('Username', h('input', { id: 'username', name: 'username', required: true, autocomplete: 'username', maxlength: 20 }),
    isLogin ? null : '3–20 letters, numbers, _ or -'),
  field('Password', h('input', {
    id: 'password', name: 'password', type: 'password', required: true, minlength: isLogin ? null : 8,
    autocomplete: isLogin ? 'current-password' : 'new-password',
  }), isLogin ? null : 'At least 8 characters'),
  err,
  h('button', { class: 'btn primary', type: 'submit' }, isLogin ? 'Log in' : 'Sign up'),
  h('div', { class: 'alt' }, isLogin ? 'New here? ' : 'Already have an account? ',
    h('a', { href: `#/${isLogin ? 'signup' : 'login'}${next ? `?next=${encodeURIComponent(next)}` : ''}` }, isLogin ? 'Create an account' : 'Log in'))));
  view.querySelector('input').focus();
}

function uploadPage() {
  if (!me) { location.hash = '#/login?next=upload'; return; }
  const err = h('div', { class: 'error' });
  const bar = h('div');
  const progress = h('div', { class: 'progress' }, bar);
  const fileHint = h('div', { class: 'hint' });
  const fileInput = h('input', { id: 'file', name: 'file', type: 'file' });
  const fileField = h('div', { class: 'field' }, h('label', { for: 'file' }, 'Game file'), fileInput, fileHint);
  const linkField = field('Link to your game', h('input', { id: 'linkUrl', name: 'linkUrl', type: 'url', placeholder: 'https://…' }),
    'e.g. an itch.io page, Google Play listing or GitHub release');

  const kindOpt = (value, title, desc, checked) => h('label', { class: 'kind' },
    h('input', { type: 'radio', name: 'kind', value, checked, onchange: syncKind }),
    h('b', null, title), h('small', null, desc));

  function syncKind() {
    const kind = view.querySelector('input[name=kind]:checked').value;
    fileField.hidden = kind === 'link';
    linkField.hidden = kind !== 'link';
    fileInput.required = kind !== 'link';
    linkField.querySelector('input').required = kind === 'link';
    if (kind === 'html5') {
      fileInput.accept = '.zip,.html,.htm';
      fileHint.textContent = 'A .zip with index.html inside (exports from Godot, Unity WebGL, GameMaker, Construct, Phaser, etc.) or a single .html file.';
    } else {
      fileInput.accept = '';
      fileHint.textContent = 'Any file people download: .apk for Android, .zip/.exe for PC, etc.';
    }
  }

  const form = h('form', {
    class: 'form',
    onsubmit: (e) => {
      e.preventDefault();
      err.textContent = '';
      const data = new FormData(form);
      if (!fileInput.files.length) data.delete('file');
      const thumbInput = form.elements.thumb;
      if (!thumbInput.files.length) data.delete('thumb');
      const btn = form.querySelector('button[type=submit]');
      btn.disabled = true;
      progress.style.display = 'block';
      const xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/games');
      xhr.setRequestHeader('X-Requested-With', 'arcade');
      xhr.upload.onprogress = (ev) => { if (ev.lengthComputable) bar.style.width = `${(ev.loaded / ev.total) * 100}%`; };
      xhr.onload = () => {
        btn.disabled = false;
        progress.style.display = 'none';
        bar.style.width = '0';
        let r = {};
        try { r = JSON.parse(xhr.responseText); } catch { /* ignore */ }
        if (xhr.status === 201) { toast('Your game is live!'); location.hash = `#/game/${r.id}`; } else err.textContent = r.error || 'Upload failed.';
      };
      xhr.onerror = () => { btn.disabled = false; progress.style.display = 'none'; err.textContent = 'Upload failed. Check your connection.'; };
      xhr.send(data);
    },
  },
  h('h1', null, 'Post a game'),
  h('p', { class: 'meta', style: 'margin:0' }, 'Share something you made. Everyone can find and play it.'),
  field('Title', h('input', { id: 'title', name: 'title', required: true, maxlength: 80 })),
  h('div', { class: 'field' }, h('label', null, 'How do people play it?'),
    h('div', { class: 'kinds' },
      kindOpt('html5', 'In the browser', 'Upload a web build. Plays right on the site.', true),
      kindOpt('download', 'Download', 'Upload a file, like an Android .apk or a PC build.'),
      kindOpt('link', 'Link', 'Your game lives somewhere else, like itch.io or Google Play.'))),
  fileField,
  linkField,
  field('Description', h('textarea', { id: 'description', name: 'description', maxlength: 4000, placeholder: 'What is it? How do you play? Controls?' })),
  field('Genre', h('select', { id: 'genre', name: 'genre' }, genres.map((g) => h('option', { value: g, selected: g === 'Other' }, g)))),
  field('Cover image (optional)', h('input', { id: 'thumb', name: 'thumb', type: 'file', accept: 'image/png,image/jpeg,image/gif,image/webp' }),
    'PNG, JPG, GIF or WebP. A wide 16:10 picture looks best.'),
  err,
  progress,
  h('button', { class: 'btn primary', type: 'submit' }, 'Publish game'));
  view.replaceChildren(form);
  syncKind();
}

async function editPage(id) {
  const { game: g } = await api(`/games/${id}`);
  if (!me || me.id !== g.authorId) { location.hash = `#/game/${id}`; return; }
  const err = h('div', { class: 'error' });
  view.replaceChildren(h('form', {
    class: 'form',
    onsubmit: async (e) => {
      e.preventDefault();
      const f = e.target.elements;
      try {
        await api(`/games/${id}`, { method: 'PATCH', body: { title: f.title.value, description: f.description.value, genre: f.genre.value } });
        toast('Saved');
        location.hash = `#/game/${id}`;
      } catch (ex) { err.textContent = ex.message; }
    },
  },
  h('h1', null, 'Edit game'),
  field('Title', h('input', { id: 'title', name: 'title', required: true, maxlength: 80, value: g.title })),
  field('Description', Object.assign(h('textarea', { id: 'description', name: 'description', maxlength: 4000 }), { value: g.description })),
  field('Genre', h('select', { id: 'genre', name: 'genre' }, genres.map((x) => h('option', { value: x, selected: x === g.genre }, x)))),
  err,
  h('div', { class: 'player-bar' },
    h('button', { class: 'btn primary', type: 'submit' }, 'Save'),
    h('a', { class: 'btn ghost', href: `#/game/${id}` }, 'Cancel'))));
}

async function userPage(name) {
  const [{ user }, { games }] = await Promise.all([
    api(`/users/${encodeURIComponent(name)}`),
    api(`/games?${new URLSearchParams({ user: name, limit: 100 })}`),
  ]);
  document.title = `${user.username} · Keystone Arcade`;
  const isMe = me && me.id === user.id;
  const bio = h('p', null, user.bio || (isMe ? 'Add a short bio so players know who you are.' : `Joined ${ago(user.createdAt)}`));
  view.replaceChildren(
    h('div', { class: 'profile-head' },
      h('div', { class: 'avatar', style: gradient(user.username) }, user.username.charAt(0).toUpperCase()),
      h('div', { style: 'flex:1;min-width:200px' },
        h('h1', null, user.username),
        bio,
        h('div', { class: 'meta', style: 'margin-top:6px' }, plural(games.length, 'game'))),
      isMe ? h('button', {
        class: 'btn',
        onclick: async () => {
          const next = prompt('Your bio (max 500 characters):', user.bio);
          if (next == null) return;
          await api('/me', { method: 'PATCH', body: { bio: next } });
          user.bio = next.trim();
          bio.textContent = user.bio || 'Add a short bio so players know who you are.';
        },
      }, 'Edit bio') : null),
    games.length ? h('div', { class: 'grid' }, games.map(gameCard))
      : h('div', { class: 'empty' }, isMe ? h('span', null, 'You haven\'t posted a game yet. ', h('a', { href: '#/upload' }, 'Post one now')) : 'No games yet.'));
}

// ---------- router ----------
async function route() {
  const [pathPart, query = ''] = location.hash.replace(/^#\/?/, '').split('?');
  const params = new URLSearchParams(query);
  const [page, arg] = pathPart.split('/').map(decodeURIComponent);
  document.title = 'Keystone Arcade';
  window.scrollTo(0, 0);
  try {
    if (!page) await homePage(params);
    else if (page === 'game' && arg) await gamePage(arg);
    else if (page === 'edit' && arg) await editPage(arg);
    else if (page === 'user' && arg) await userPage(arg);
    else if (page === 'upload') uploadPage();
    else if (page === 'login' || page === 'signup') authPage(page, params);
    else view.replaceChildren(h('div', { class: 'empty' }, 'Page not found. ', h('a', { href: '#/' }, 'Go home')));
  } catch (err) {
    view.replaceChildren(h('div', { class: 'empty' }, err.message, ' ', h('a', { href: '#/' }, 'Go home')));
  }
}

document.getElementById('search').addEventListener('submit', (e) => {
  e.preventDefault();
  const q = e.target.elements.q.value.trim();
  location.hash = q ? `#/?${new URLSearchParams({ q })}` : '#/';
});

window.addEventListener('hashchange', route);
(async () => {
  try {
    [{ user: me }, { genres }] = await Promise.all([api('/me'), api('/genres')]);
  } catch { /* offline: render anyway */ }
  renderNav();
  route();
})();
