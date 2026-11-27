// mochypck: standalone .mochypck binary format, module api v4 sdk, bridge, and cli compiler
@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mochypck"

include(":api")
include(":pack-format")
include(":bridge")
include(":compiler")
