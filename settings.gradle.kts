pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "LintehJournal"

include(":composeApp")
include(":core:platform")
include(":core:ui")
include(":core:network")
include(":core:database")
include(":core:api-avers-v4")
