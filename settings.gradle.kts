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

rootProject.name = "KopfFrei"

include(":app")

include(":core:common")
include(":core:database")
include(":core:designsystem")
include(":core:security")
include(":core:reminders")

include(":feature:dashboard")
include(":feature:notes")
include(":feature:journal")
include(":feature:calendar")
include(":feature:tasks")
include(":feature:focus")
include(":feature:settings")
include(":feature:widgets")
