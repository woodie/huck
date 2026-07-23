import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
    id("org.jetbrains.compose") version "1.11.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.netpress.kotidy") version "0.1.0"
}

group = "com.netpress"
version = "0.3.1"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)

    // Res/painterResource/etc. for composeResources/drawable (large.png, small.png --
    // the app icon assets) -- this dependency is what actually generates the Res class,
    // separate from the compose.resources{} config block below which just names its package.
    // Direct coordinate, not the compose.components.resources Gradle-plugin alias --
    // deprecated the same way compose.material was ("Specify dependency directly").
    implementation("org.jetbrains.compose.components:components-resources:1.11.0")

    // Direct coordinate, not the compose.material Gradle-plugin alias --
    // that alias is deprecated as of Compose Multiplatform 1.10+ ("Specify
    // dependency directly"). Version tracks the compose plugin version above.
    implementation("org.jetbrains.compose.material:material:1.11.0")

    // Icons.Filled.Refresh for ScanGridView's toolbar -- direct coordinate for the
    // same "specify dependency directly" reason as compose.material above. Doesn't
    // track the 1.11.0 plugin version like the others here: confirmed via Maven
    // Central that this artifact's last published version is 1.7.3 -- JetBrains
    // stopped cutting new releases of it after that, so pinned there instead. The
    // icon set's API (Icons.Filled.*) hasn't changed since, so this is safe to mix
    // with the newer 1.11.0 runtime.
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")

    // Icons.Filled.OpenInNew/CloudDownload/FileDownload for the right-click context
    // menu (see ScanGridView.kt) -- material-icons-core above only bundles the ~50
    // most-common icons, and none of those cover "download"/"open externally". Same
    // 1.7.3 pin as material-icons-core, for the same reason (last version published
    // under this coordinate; the icon set itself hasn't changed since).
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // Composite-build dependency (see settings.gradle.kts) -- Gradle substitutes
    // this coordinate with the ../humane-kotlin project automatically because
    // that build's own group:name (see its build.gradle.kts/settings.gradle.kts)
    // match. No version needed for a composite-build substitution.
    implementation("com.netpress:humane-kotlin")

    // AppModel's connect()/etc. are suspend functions, matching zouk's async
    // AppModel -- version matches next-caltrain-kotlin's pinned coroutines.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // files.json parsing (ScanEntry list) -- JetBrains' own JSON library,
    // the closest Kotlin equivalent to Go/Ruby's stdlib encoding/json rather
    // than a third-party dependency.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Uses JDK's built-in java.net.http.HttpClient for ScanClient -- no
    // separate HTTP library dependency, matching Go/Ruby's "stdlib first"
    // posture.

    // Renders AppModel.thumbnail(for:)'s first PDF page to a BufferedImage. zouk's own
    // equivalent (PDFKit's PDFDocument/page.thumbnail(of:for:)) is macOS-only, so this is the
    // JVM stand-in -- pure Java, no native dependency, the JVM PDF renderer docs/COWORK.md
    // already flagged as the intended fix for real thumbnails.
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

compose.resources {
    // Confirmed via search that composeResources (drawable/large.png, small.png)
    // works in this plain kotlin("jvm") + compose.desktop setup, not just full
    // Kotlin Multiplatform modules -- packageOfResClass just fixes the generated
    // Res class's package so Res.drawable.large/small are predictable to import.
    packageOfResClass = "com.netpress.huck.resources"
}

// No ktlint { filter { ... } } block here -- two different forms of it were both
// silently ignored against the Compose plugin's generated Res.kt/Drawable0.main.kt
// (a known, still-open ktlint-gradle regression since 12.1.0, confirmed against
// real output rather than assumed). The exclusion that actually works lives in
// .editorconfig's [build/generated/**] section instead -- see the comment there.

compose.desktop {
    application {
        mainClass = "com.netpress.huck.MainKt"

        nativeDistributions {
            // Msi is the one this repo actually cares about -- see docs/COWORK.md
            // for why producing it doesn't require touching real Windows hardware.
            // Dmg is included too since local development happens on macOS, and
            // "package for the OS I'm building on" needs a target format for that
            // OS as well.
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Huck"
            packageVersion = "0.3.1"

            // jpackage builds the bundled app a real, jlink-trimmed JDK image, not
            // a full JRE -- by default that image only includes whatever modules
            // this Gradle plugin decides it needs, and nothing here declares that
            // list explicitly. Confirmed as a real, launch-breaking bug on the
            // real installed v0.2.0 .msi: connecting threw
            // "java/net/http/HttpClient" (a NoClassDefFoundError, since
            // ScanClient.kt's HttpClient import lives in the java.net.http
            // module, which wasn't in the trimmed runtime) and closed the app on
            // OK. FileDialog/Desktop.open (java.desktop) and
            // Preferences.userNodeForPackage (java.prefs) are real candidates to
            // hit the exact same gap the moment those code paths run too --
            // they're all used from inside private/suspend functions rather than
            // top-level, easy-to-detect call sites. Rather than hand-enumerate
            // every JDK module actually reachable at runtime (and risk another
            // round of real-hardware-only whack-a-mole with no local Kotlin
            // toolchain to test against), includeAllModules bundles the complete
            // JDK instead of a trimmed one -- a real installer-size tradeoff
            // (JetBrains' own documented escape hatch for exactly this failure
            // mode), accepted here since correctness beats install size for a
            // single-purpose utility app like this one.
            includeAllModules = true

            // Without this, jpackage falls back to a generic default icon (a
            // plain coffee-cup/Duke-style placeholder) for the installed .exe,
            // Start Menu entry, and uninstaller listing on Windows. icons/icon.ico
            // is a multi-resolution icon (16 through 256px) generated from the
            // same small.png used for AppIconImage's in-app icon, so the
            // packaged app and the in-app icon actually match.
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Replaces the custom RSpec/ginkgo-fd-style TestListener that used to live
// directly in this file (copy-pasted byte-for-byte from next-caltrain-kotlin,
// also mirrored into humane-kotlin) -- see kotidy's own docs/COWORK.md for
// why it was extracted into a real plugin instead of staying a hand-synced
// block, and settings.gradle.kts's includeBuild comment for the
// composite-build mechanism. "fs" is the closest existing style to what this
// project's output looked like before (checkmark + gray name for passes) --
// not byte-identical, since the old ad hoc block's fail/skip glyphs didn't
// actually match any single named style; see kotidy's README for the real
// Mocha-spec-format shape this now renders instead.
kotidy {
    style = "fs"
}
