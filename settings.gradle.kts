pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "NexoAgenda"
include(":androidApp", ":desktopApp", ":shared", ":webApp")
