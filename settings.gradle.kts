pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MantraSiddhi"
include(":app")
project(":app").projectDir = file("frontend/android")
include(":core:domain", ":core:data", ":core:resources")
include(":macrobenchmark")
