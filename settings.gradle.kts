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
        // OwnTV Core / player-core are GPLv3. See THIRD_PARTY_NOTICES.md.
        maven {
            name = "OwnTV"
            url = uri("https://ahxn00.github.io/OwnTV_Core/maven")
            content { includeGroup("tv.own.owntv") }
        }
    }
}

rootProject.name = "Matchday"
include(":app")

// OwnTV Core is consumed from the same-directory GPLv3 checkout as a composite build: its
// group:name (tv.own.owntv:core / :player-core) matches the catalog coordinates, so Gradle
// substitutes the published 1.0.68 artifact with the local source — keeping the tree the
// single source of truth for Matchday build reproducibility. See THIRD_PARTY_NOTICES.md.
includeBuild("third_party/owntv-core")
include(":domain")
