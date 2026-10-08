# Keystone for the web

Builds Keystone to run in a web browser from the **same Kotlin code** as the Android app:
the title screen, intro, class and character creation, the 3D world, HUD, map, menus,
sound and saving. The game sources are read straight out of `../Keystone.zip`, so
uploading a new zip updates the web version too.

## How it works

- `build.gradle.kts` unpacks every game `.kt` file (except Google Play's `CloudSave.kt`),
  the images and music from `Keystone.zip`, and generates the `R` class from its `res/`
  folder. Two things are adapted as text while unpacking, and the build stops with a
  message if the Android code changes under them:
  - `Sound.kt`'s playback loop, which the browser pulls a block at a time instead
  - title-screen wording that mentions Google Play
- The Compose screens run on Compose Multiplatform (1.7.3, drawn with Skia/WebAssembly)
  on a transparent layer; the 3D world runs on a WebGL canvas underneath.
- `src/jsMain/kotlin/shims/` provides browser versions of the Android and Java pieces the
  game uses, so the game files compile **unchanged**:
  - `GLES20` on WebGL, `Matrix` ported from AOSP, `GLSurfaceView` and `AndroidView`
  - `Bitmap`, `Canvas` text and `Paint` on Skia, `painterResource` for the drawables
  - `AudioTrack` (Web Audio) and `MediaPlayer` (HTML audio)
  - `SharedPreferences` on browser storage
  - `java.util.Random` with the JVM's exact algorithm (same treasure and quest placement
    as the phone; checked by `src/jsTest`)
  - `ByteBuffer`, threads, executors and concurrent collections on a frame-sliced queue
- `WebCloudSave.kt` keeps Save / Load working: Save backs the adventure up, Load restores
  it, and a dead hardcore hero's backup is buried, as on Android.
- `WebMain.kt` is the browser host: canvases, frame loop, fonts and images, and keyboard
  and mouse on top of the game's own touch controls.
- `src/jsMain/resources/fonts/` holds small subsets of Noto Sans, Noto Sans Symbols and
  Twemoji (licenses next to them), since a browser's Skia has no system fonts.

## Saving on Keystone Arcade

The site's player gives games a `localStorage` that the site keeps: in the player's
account when logged in, otherwise in their browser. Keystone's saves go there, so
progress follows the player between devices. The title screen shows who is signed in.

## Build

```sh
cd keystone-web
gradle jsBrowserDistribution   # output: build/dist/js/productionExecutable/
gradle jsNodeTest              # checks the Random port against the JVM
```

To post it on Keystone Arcade, zip `index.html`, `keystone.js`, `skiko.js`,
`skiko.wasm`, `fonts/` and `res/` from the output folder and post the zip as an
"In the browser" game. GitHub Actions (`.github/workflows/web.yml`) builds this zip on
every push to `main` that touches `Keystone.zip` or this folder.

## Controls

The on-screen touch controls work as on the phone. On a computer also: WASD / arrows
move · Shift run · Space jump · E or F use · V camera · 1-4 quick items · right click
captures the mouse for looking (Esc lets go), then left click attacks.
