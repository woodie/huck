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

// No published artifact for humane-kotlin yet -- this is a Gradle composite
// build, not a version dependency. Requires humane-kotlin checked out as a
// sibling directory (../humane-kotlin relative to this file). See
// docs/COWORK.md for why, and for what CI does differently to get that same
// sibling layout on a fresh runner.
includeBuild("../humane-kotlin")
