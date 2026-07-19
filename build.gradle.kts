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
version = "0.1.0"

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

ktlint {
    // The Compose plugin registers its generated Res.kt/Drawable0.main.kt (under
    // build/generated/compose/resourceGenerator) as a real Kotlin source dir on the
    // main source set, so ktlint picks it up too -- it's not ours to fix (naming,
    // formatting, all of it is JetBrains' generated code), so exclude it outright.
    filter {
        exclude { it.file.path.contains("${File.separator}generated${File.separator}") }
    }
}

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
            packageVersion = "0.1.0"
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Custom RSpec/ginkgo-fd-style console reporter -- same as humane-kotlin's
    // build.gradle.kts (copied from next-caltrain-kotlin originally; see that
    // file's own comments for the full reasoning). No test source exists here
    // yet, but this is wired up now so it's already in place the moment real
    // tests land.
    var lastPath: List<String> = emptyList()

    val colorEnabled = System.getenv("NO_COLOR") == null
    val reset = "[0m"
    val green = "[32m"
    val red = "[31m"
    val cyan = "[36m"
    val gray = "[90m"

    fun ansi(
        code: String,
        text: String,
    ) = if (colorEnabled) "$code$text$reset" else text

    fun ancestry(descriptor: TestDescriptor): List<String> {
        val names = mutableListOf<String>()
        var d = descriptor.parent
        while (d != null) {
            if (!d.name.startsWith("Gradle Test")) names.add(0, d.name)
            d = d.parent
        }
        return names
    }

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

                val shared = path.zip(lastPath).takeWhile { (a, b) -> a == b }.count()
                for (depth in shared until ancestors.size) {
                    if (depth == 0) println()
                    println("  ".repeat(depth) + ancestors[depth])
                }

                val line =
                    when (result.resultType) {
                        TestResult.ResultType.SUCCESS ->
                            "${ansi(green, "✔")} ${ansi(gray, testDescriptor.name)}"

                        TestResult.ResultType.SKIPPED ->
                            ansi(cyan, "○ ${testDescriptor.name}")

                        else ->
                            ansi(red, "✖ ${testDescriptor.name}")
                    }
                println("  ".repeat(ancestors.size) + line)
                if (result.resultType == TestResult.ResultType.FAILURE) {
                    result.exceptions.forEach { e ->
                        println("  ".repeat(ancestors.size + 1) + ansi(red, e.message ?: e.toString()))
                    }
                }

                lastPath = path
            }
        },
    )
}
