pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
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
