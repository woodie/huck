import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
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

    // Direct coordinate, not the compose.material Gradle-plugin alias --
    // that alias is deprecated as of Compose Multiplatform 1.10+ ("Specify
    // dependency directly"). Version tracks the compose plugin version above.
    implementation("org.jetbrains.compose.material:material:1.11.0")

    // Composite-build dependency (see settings.gradle.kts) -- Gradle substitutes
    // this coordinate with the ../humane-kotlin project automatically because
    // that build's own group:name (see its build.gradle.kts/settings.gradle.kts)
    // match. No version needed for a composite-build substitution.
    implementation("com.netpress:humane-kotlin")
}

compose.desktop {
    application {
        mainClass = "MainKt"

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
    fun ansi(code: String, text: String) = if (colorEnabled) "$code$text$reset" else text

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

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            val ancestors = ancestry(testDescriptor)
            val path = ancestors + testDescriptor.name

            val shared = path.zip(lastPath).takeWhile { (a, b) -> a == b }.count()
            for (depth in shared until ancestors.size) {
                if (depth == 0) println()
                println("  ".repeat(depth) + ancestors[depth])
            }

            val line = when (result.resultType) {
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
    })
}
