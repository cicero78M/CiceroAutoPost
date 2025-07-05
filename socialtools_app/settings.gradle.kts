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


// settings.gradle
include ":instagram4j"
project(":instagram4j").projectDir = new File(rootDir, "external/instagram4j")include ":instagram4j"



