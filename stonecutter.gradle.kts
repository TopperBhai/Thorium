plugins {
    id("dev.kikugie.stonecutter")
    // Both Loom flavors are declared here (apply false) so that node build
    // scripts under versions/<mc>/ can apply the right one by id without
    // repeating the version.
    id("net.fabricmc.fabric-loom-remap") version "1.19.1" apply false
    id("net.fabricmc.fabric-loom") version "1.19.1" apply false
}

// ---------------------------------------------------------------------------
// Root project tasks.
//
// Stonecutter (with mapBuilds configured in settings.gradle.kts) treats this
// file as the root project's build script - a root build.gradle.kts would not
// be evaluated at all. Aggregate tasks therefore live here.
// ---------------------------------------------------------------------------
tasks.register<Copy>("buildAll") {
    group = "build"
    description = "Builds Thorium release jars for every registered Minecraft version into build/dist without test clutter."
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // Only compile the jars, avoiding heavy test suites and redundant rebuilds
    dependsOn(subprojects.map { "${it.path}:assemble" })
    from(subprojects.map { it.layout.buildDirectory.dir("libs") })
    include("*.jar")
    exclude("*-sources.jar", "*-dev.jar")
    into(layout.buildDirectory.dir("dist"))
}

// The node treated as "current" by clicks/IDE/run configurations.
stonecutter active "26.3" /* [SC] DO NOT EDIT */
