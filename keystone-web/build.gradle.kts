// Builds Keystone for web browsers from the same Kotlin sources as the Android app.
// Every game file, including the Compose screens, is read straight out of ../Keystone.zip,
// so the web build always matches the latest uploaded Android project. Android-only
// services (OpenGL, audio, Play Games, resources) get web versions under src/jsMain/kotlin.

import java.util.zip.ZipFile

plugins {
    kotlin("multiplatform") version "2.2.0"
    // 1.7.3 is the newest Compose Multiplatform whose web build needs nothing from Google's Maven.
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
}

val gameZip = rootProject.file("../Keystone.zip")

/** Google Play cloud saving can't run in a browser; the web build has its own CloudSave. */
val androidOnlyFiles = listOf("CloudSave.kt")

val gameSources = layout.buildDirectory.dir("game-src")
val gameResources = layout.buildDirectory.dir("game-res")
val generatedSources = layout.buildDirectory.dir("generated-src")

/**
 * The one place the game code is adapted rather than shimmed: Sound.kt streams audio from an
 * endless loop on its own thread, which a browser can't do. The browser pulls the same mixer
 * a block at a time instead. Fails loudly if Sound.kt changes so this needs updating.
 */
val soundLoop = """
            while (running) {
                mixer.fill(out)
                track.write(out, 0, out.size)
            }
""".trimIndent()

val soundLoopWeb = """
            WebAudioOut.stream(track, { running }) {
                mixer.fill(out)
                out
            }
            return
""".trimIndent()

/**
 * Title screen wording that names Google Play. On the web, Save and Load keep a backup of
 * the adventure (in the player's Keystone Arcade account when playing there).
 */
val webWording = mapOf(
    "Save and Load use your Google cloud save." to "Save backs up your adventure. Load brings the backup back.",
    "Save and Load need Google Play Games sign-in." to "Save backs up your adventure. Load brings the backup back.",
    "Replace the adventure on this phone with your cloud save?" to "Replace your adventure with your saved backup?",
    "your cloud save is replaced the next time you save." to "your backup is replaced the next time you save.",
)

val extractGameSources by tasks.registering(Sync::class) {
    inputs.file(gameZip)
    from(zipTree(gameZip)) {
        include("app/src/main/java/**/*.kt")
        androidOnlyFiles.forEach { exclude("**/$it") }
        eachFile { path = path.removePrefix("app/src/main/java/") }
        includeEmptyDirs = false
    }
    into(gameSources)
    doLast {
        val sound = gameSources.get().file("com/keystone/rpg/Sound.kt").asFile
        val text = sound.readText()
        val indented = soundLoop.prependIndent("            ").trimStart()
        check(text.contains(indented)) { "Sound.kt's playback loop changed; update soundLoop in keystone-web/build.gradle.kts" }
        sound.writeText(text.replace(indented, soundLoopWeb.prependIndent("            ").trimStart()))

        val main = gameSources.get().file("com/keystone/rpg/MainActivity.kt").asFile
        var mainText = main.readText()
        for ((from, to) in webWording) {
            check(mainText.contains(from)) { "MainActivity.kt no longer says \"$from\"; update webWording in keystone-web/build.gradle.kts" }
            mainText = mainText.replace(from, to)
        }
        main.writeText(mainText)
    }
}

/** Images and music from the Android res/ folder, served next to the web page. */
val extractGameResources by tasks.registering(Sync::class) {
    from(zipTree(gameZip)) {
        include("app/src/main/res/drawable*/*.png", "app/src/main/res/raw/*.mp3", "app/src/main/res/raw/*.ogg")
        // drawable-nodpi/x.png -> res/drawable/x.png, raw/x.mp3 -> res/raw/x.mp3
        eachFile {
            val dir = path.substringAfterLast("/res/").substringBefore("/")
            path = "res/" + (if (dir == "raw") "raw" else "drawable") + "/" + name
        }
        includeEmptyDirs = false
    }
    into(gameResources)
}

/** Generates the R class (ids for drawables, raw files and strings) from the zip's res/ folder. */
val generateR by tasks.registering {
    inputs.file(gameZip)
    outputs.dir(generatedSources)
    doLast {
        val drawables = sortedSetOf<String>()
        val raws = sortedSetOf<String>()
        val strings = sortedMapOf<String, String>()
        ZipFile(gameZip).use { zip ->
            for (e in zip.entries()) {
                val n = e.name
                if (!n.startsWith("app/src/main/res/") || e.isDirectory) continue
                val dir = n.removePrefix("app/src/main/res/").substringBefore("/")
                val file = n.substringAfterLast("/")
                val base = file.substringBefore(".")
                when {
                    dir.startsWith("drawable") && file.endsWith(".png") -> drawables += base
                    dir == "raw" && (file.endsWith(".mp3") || file.endsWith(".ogg")) -> raws += file
                    dir == "values" && file.endsWith(".xml") -> {
                        val xml = zip.getInputStream(e).bufferedReader().readText()
                        Regex("<string name=\"([^\"]+)\"[^>]*>([^<]*)</string>").findAll(xml).forEach {
                            strings[it.groupValues[1]] = it.groupValues[2]
                        }
                    }
                }
            }
        }
        var id = 0x7f000001
        val out = StringBuilder()
        out.appendLine("// Generated from Keystone.zip's res/ folder by keystone-web/build.gradle.kts. Do not edit.")
        out.appendLine("package com.keystone.rpg\n")
        out.appendLine("object R {")
        out.appendLine("    object drawable {")
        val drawableIds = drawables.associateWith { id++ }
        drawableIds.forEach { (n, v) -> out.appendLine("        const val $n = $v") }
        out.appendLine("    }")
        out.appendLine("    object raw {")
        val rawIds = raws.associateWith { id++ }
        rawIds.forEach { (f, v) -> out.appendLine("        const val ${f.substringBefore(".")} = $v") }
        out.appendLine("    }")
        out.appendLine("    object string {")
        val stringIds = strings.keys.associateWith { id++ }
        stringIds.forEach { (n, v) -> out.appendLine("        const val $n = $v") }
        out.appendLine("    }")
        out.appendLine("}\n")
        out.appendLine("/** Resource ids to files (served under res/) and string values, for the web shims. */")
        out.appendLine("object WebResources {")
        out.appendLine("    val files: Map<Int, String> = mapOf(")
        drawableIds.forEach { (n, v) -> out.appendLine("        $v to \"res/drawable/$n.png\",") }
        rawIds.forEach { (f, v) -> out.appendLine("        $v to \"res/raw/$f\",") }
        out.appendLine("    )")
        out.appendLine("    val names: Map<String, Int> = mapOf(")
        drawableIds.forEach { (n, v) -> out.appendLine("        \"drawable/$n\" to $v,") }
        rawIds.forEach { (f, v) -> out.appendLine("        \"raw/${f.substringBefore(".")}\" to $v,") }
        stringIds.forEach { (n, v) -> out.appendLine("        \"string/$n\" to $v,") }
        out.appendLine("    )")
        out.appendLine("    val strings: Map<Int, String> = mapOf(")
        stringIds.forEach { (n, v) -> out.appendLine("        $v to \"${strings.getValue(n).replace("\\", "\\\\").replace("\"", "\\\"")}\",") }
        out.appendLine("    )")
        out.appendLine("    val drawables: List<Int> = listOf(${drawableIds.values.joinToString()})")
        out.appendLine("}")
        val f = generatedSources.get().file("com/keystone/rpg/R.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(out.toString())
    }
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
            kotlin.srcDir(generateR)
            resources.srcDir(extractGameResources)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
