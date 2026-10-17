pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
            mavenContent {
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
                includeGroup("fabric-loom")
            }
        }
    }
}

plugins {
    // Auto-provisions missing JDK toolchains (e.g. Java 25 for Minecraft 26.1+).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    // Multi-version source preprocessor: one unified source tree -> N per-version builds.
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // ---------------------------------------------------------------
        // Thorium version matrix:
        //
        // 1.21.1 - classic era (Minecraft obfuscated, remapped to intermediary)
        // 26.3   - modern era (Minecraft >= 26.1 ships unobfuscated Mojang names)
        // ---------------------------------------------------------------
        versions("1.21.1", "26.1", "26.2", "26.3")
        vcsVersion = "26.3"

        // Each node project uses the build script inside its own directory:
        //   net.fabricmc.fabric-loom-remap -> obfuscated versions (< 26.1)
        //   net.fabricmc.fabric-loom       -> unobfuscated versions (>= 26.1)
        mapBuilds { _, node -> "versions/${node.project}/build.gradle.kts" }
    }
}

rootProject.name = "Thorium"
