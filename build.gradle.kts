import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
    id("org.jetbrains.compose") version "1.11.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.netpress"
version = "0.2.0"

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
            packageVersion = "0.2.0"
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Custom RSpec/ginkgo-fd-style console reporter, replacing gradle-test-logger-plugin.
    // The plugin's mocha theme gave genuine nested indentation with checkmarks (the
    // shape we want) but inserts a blank line between every describe/context group,
    // hardcoded into its theme with no config flag to disable. This hooks Gradle's own
    // TestListener API directly -- the same mechanism the plugin itself uses under the
    // hood -- to walk the real nested TestDescriptor.parent chain and print a dense
    // tree with no blank-line padding. No final summary line of our own -- Gradle's
    // own "BUILD SUCCESSFUL"/"BUILD FAILED" already closes out the run.
    //
    // Copied byte-for-byte from next-caltrain-kotlin's app/build.gradle.kts (also
    // mirrored into humane-kotlin) -- kept identical across all three repos on purpose,
    // right down to the SCREAMING_SNAKE_CASE constant names (see .editorconfig's
    // ktlint_standard_property-naming disable, which is what stops ktlintFormat from
    // silently lowercasing them back to reset/green/red/cyan/gray on every run).
    //
    // Gradle's tree has two synthetic wrapper suites above the real top-level describe()
    // ("Gradle Test Run :test" and "Gradle Test Executor N"); ancestry() filters those
    // out by name prefix, which is the standard trick for custom Gradle test listeners.
    var lastPath: List<String> = emptyList()

    // Respect the NO_COLOR convention (https://no-color.org/) for anyone piping
    // this into a log file or a terminal that mangles escape codes.
    val colorEnabled = System.getenv("NO_COLOR") == null
    val RESET = "[0m"
    val GREEN = "[32m"
    val RED = "[31m"
    val CYAN = "[36m"
    val GRAY = "[90m"

    fun ansi(
        code: String,
        text: String,
    ) = if (colorEnabled) "$code$text$RESET" else text

    fun ancestry(descriptor: TestDescriptor): List<String> {
        val names = mutableListOf<String>()
        var d = descriptor.parent
        while (d != null) {
            if (!d.name.startsWith("Gradle Test")) names.add(0, d.name)
            d = d.parent
        }
        return names
    }

    // Reset dedupe state at actual task-execution time, not here at configuration
    // time. doFirst always re-runs on every invocation regardless of Gradle's
    // configuration cache, so this is the one safe place to reset from -- matches
    // caltrain's own comment on this same line, even though huck doesn't currently
    // set org.gradle.configuration-cache=true itself (no gradle.properties here yet).
    doFirst {
        lastPath = emptyList()
    }

    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}

            override fun afterSuite(
                suite: TestDescriptor,
                result: TestResult,
            ) {}

            override fun beforeTest(testDescriptor: TestDescriptor) {}

            override fun afterTest(
                testDescriptor: TestDescriptor,
                result: TestResult,
            ) {
                val ancestors = ancestry(testDescriptor)
                val path = ancestors + testDescriptor.name

                // Print only the part of the path not already printed for the previous
                // test -- the "dedupe shared prefix" trick that produces a real nested
                // tree from a flat stream of leaf-test callbacks, with no blank lines.
                val shared = path.zip(lastPath).takeWhile { (a, b) -> a == b }.count()
                for (depth in shared until ancestors.size) {
                    // depth == 0 here means ancestors[0] -- the fully-qualified spec class
                    // name (e.g. com.netpress.huck.AppModelSpec) -- is about to be printed
                    // for a new top-level suite. A blank line goes before every one of
                    // those, unconditionally (including the first), so each suite's block
                    // visually stands apart from whatever came before it.
                    if (depth == 0) println()
                    println("  ".repeat(depth) + ancestors[depth])
                }

                // Mocha's own spec reporter colors the checkmark green and dims the title
                // for passes; failures and pending get a single solid color instead.
                val line =
                    when (result.resultType) {
                        TestResult.ResultType.SUCCESS ->
                            "${ansi(GREEN, "✔")} ${ansi(GRAY, testDescriptor.name)}"

                        TestResult.ResultType.SKIPPED ->
                            ansi(CYAN, "○ ${testDescriptor.name}")

                        else ->
                            ansi(RED, "✖ ${testDescriptor.name}")
                    }
                println("  ".repeat(ancestors.size) + line)
                if (result.resultType == TestResult.ResultType.FAILURE) {
                    result.exceptions.forEach { e ->
                        println("  ".repeat(ancestors.size + 1) + ansi(RED, e.message ?: e.toString()))
                    }
                }

                lastPath = path
            }
        },
    )
}
