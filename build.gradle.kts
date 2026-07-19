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
