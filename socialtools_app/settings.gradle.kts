pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SocialToolsApp"

include(":app")
include(":instagram4j")
project(":instagram4j").projectDir = File(rootDir, "external/instagram4j")
