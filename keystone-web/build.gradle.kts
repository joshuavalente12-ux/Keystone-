// Builds Keystone for web browsers from the same Kotlin sources as the Android app.
// The game code is read straight out of ../Keystone.zip, so the web build always
// matches the latest uploaded Android project. Android-only pieces (OpenGL, audio,
// saves, Compose screens) are replaced by web versions under src/jsMain/kotlin.

plugins {
    kotlin("multiplatform") version "2.2.0"
}

/** Screens built with Jetpack Compose and Android services: the web build has its own versions. */
val androidOnlyFiles = listOf(
    "MainActivity.kt",
    "CharacterScreen.kt",
    "SphereGridUi.kt",
    "TitleArt.kt",
    "CloudSave.kt",
    "Sound.kt",
)

val gameSources = layout.buildDirectory.dir("game-src")

val extractGameSources by tasks.registering(Sync::class) {
    from(zipTree(rootProject.file("../Keystone.zip"))) {
        include("app/src/main/java/**/*.kt")
        androidOnlyFiles.forEach { exclude("**/$it") }
        eachFile { path = path.removePrefix("app/src/main/java/") }
        includeEmptyDirs = false
    }
    into(gameSources)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "keystone.js"
            }
            // Tests run in Node (below), which needs no browser installed.
            testTask { enabled = false }
        }
        nodejs()
        binaries.executable()
        compilerOptions {
            // The game code is written for the JVM; these warnings are expected on JS.
            suppressWarnings.set(true)
        }
    }
    sourceSets {
        jsMain {
            kotlin.srcDir(extractGameSources)
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
