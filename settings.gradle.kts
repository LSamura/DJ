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
        // Official Vosk Android artifacts (offline speech recognition, ADR-020).
        maven { url = uri("https://alphacephei.com/maven/") }
    }
}

rootProject.name = "DJAssistant"

include(":app")
include(":core")
include(":feature")
include(":data")
include(":service")
include(":ui")
