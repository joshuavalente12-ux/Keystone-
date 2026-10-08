# Keystone for the web

Builds Keystone to run in a web browser (WebGL), from the **same Kotlin code** as the
Android app. The game sources are read straight out of `../Keystone.zip`, so uploading a
new zip updates the web version too.

## How it works

- `build.gradle.kts` unpacks the game's `.kt` files from `Keystone.zip` and compiles them
  with Kotlin/JS. Android-only screens and services (`MainActivity`, `CharacterScreen`,
  `SphereGridUi`, `TitleArt`, `CloudSave`, `Sound`) are left out.
- `src/jsMain/kotlin/shims/` provides browser versions of the Android and Java pieces the
  game uses, so the game files compile **unchanged**:
  - `android.opengl.GLES20` on WebGL 1, and `android.opengl.Matrix` ported from AOSP
  - `SharedPreferences` on browser storage (saves keep working)
  - `java.util.Random` with the JVM's exact algorithm (same treasure and quest placement
    as the phone; checked by `src/jsTest`)
  - `ByteBuffer`, the concurrent collections, and a background-task queue that runs chunk
    building a few milliseconds per frame
  - the small parts of Compose the game logic uses (state holders, `Color`, `Offset`)
- `src/jsMain/kotlin/com/keystone/rpg/WebMain.kt` is the browser host: canvas, frame loop,
  keyboard, mouse (pointer lock) and touch controls, and a simple HUD.

## Build

```sh
cd keystone-web
gradle jsBrowserDistribution   # output: build/dist/js/productionExecutable/
gradle jsNodeTest              # checks the Random port against the JVM
```

Open `build/dist/js/productionExecutable/index.html` through a local web server, or zip
`index.html` + `keystone.js` and post it on Keystone Arcade as a browser game.

GitHub Actions (`.github/workflows/web.yml`) builds this on every push to `main` that
touches `Keystone.zip` or this folder, and attaches `keystone-web.zip` to the run.

## Controls

WASD / arrows move · mouse looks (click the game first) · Shift run · Space jump ·
left click attack · E use · V camera · 1-4 quick items. On phones: left half walks,
right half looks, plus on-screen buttons.

## Not done yet

Title / class / character screens, the full HUD and menus, sound and music, saving to the
player's Keystone Arcade account.
