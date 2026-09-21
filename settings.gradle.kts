pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "AniBeat"

include(":app")
include(":core:model")
include(":core:common")
include(":core:design")
include(":core:network")
include(":core:database")
include(":core:playback")
include(":feature:home")
include(":feature:search")
include(":feature:browse")
include(":feature:library")
include(":feature:details")
include(":feature:player")
include(":feature:settings")
