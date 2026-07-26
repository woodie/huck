pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets Gradle auto-download a matching JDK when jvmToolchain(17) (see
    // build.gradle.kts) can't find one already installed, instead of failing
    // outright -- matches next-caltrain-kotlin's own settings.gradle.kts.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "huck"

// Composite build, not pluginManagement -- kwick is a plain library
// (testImplementation), not a Gradle plugin like kotidy, so a regular
// dependency substitution in the main body is all Gradle needs to resolve
// build.gradle.kts's testImplementation("com.netpress:kwick:...") against
// the local checkout instead of Maven Central. No published artifact yet --
// see that repo's own docs/COWORK.md "Packaging". Requires ../kwick to
// exist as a sibling checkout (matches next-caltrain-kotlin's own
// settings.gradle.kts).
includeBuild("../kwick")
