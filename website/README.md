# Keystone Arcade

A website where people post the games they make and other people play them.

- **Sign up** with a username and password.
- **Post a game** three ways:
  - **In the browser**: upload a `.zip` with an `index.html` inside (web exports from Godot, Unity WebGL, GameMaker, Construct, Phaser, …) or a single `.html` file. It plays right on the site.
  - **Download**: upload any file, like an Android `.apk` or a PC build.
  - **Link**: point to a game hosted elsewhere (itch.io, Google Play, …).
- **Browse** newest / most played / most liked, filter by type and genre, and search.
- **Like** and **comment** on games, and edit or delete your own.
- **Profiles** list each creator's games.

## Run it

Needs Node.js 22.13 or newer (it uses Node's built-in SQLite, so there's no database to install).

```sh
cd website
npm install
npm start
```

Open http://localhost:3000. A demo game, *Star Catcher*, is added the first time it starts.

Run the tests with `npm test`.

## Settings

| Variable          | Default         | What it does                                |
| ----------------- | --------------- | ------------------------------------------- |
| `PORT`            | `3000`          | Port to listen on                           |
| `DATA_DIR`        | `website/data`  | Where the database and uploaded files live  |
| `MAX_UPLOAD_MB`   | `200`           | Largest file someone can upload             |
| `MAX_UNZIPPED_MB` | `300`           | Largest size a zipped game can unzip to     |

## Putting it online

Any host that runs Node.js works (Render, Railway, Fly.io, a VPS, …). Two things to get right:

1. **Persistent storage.** Uploaded games and the database are saved in `DATA_DIR`. Point it at a persistent disk/volume, or everything is lost when the server restarts.
2. **HTTPS.** Run it behind HTTPS (most hosts do this for you) so passwords and login cookies are encrypted.

For a public site, consider also serving `/play/*` from a separate domain for extra isolation, adding rate limits on sign-up/login, and a way to report games.

## How uploaded games are kept safe

Games are code written by strangers, so:

- Browser games run in a sandboxed iframe **and** are served with a `Content-Security-Policy: sandbox` header, giving them an isolated origin. They can't read your login cookie or act as you on the site.
- Every change to the site (posting, liking, commenting, deleting) requires a custom request header that other sites and sandboxed games can't send.
- Zip files are unpacked by a strict extractor (`lib/unzip.js`) that rejects `../` paths, skips symlinks, and caps the unzipped size to stop zip bombs.
- Cover images are checked to really be PNG/JPG/GIF/WebP; downloads are always sent as file downloads, never shown as pages.
