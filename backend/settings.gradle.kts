pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Downloads the JDK 25 toolchain when it isn't installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "liftlog"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
