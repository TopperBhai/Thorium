plugins {
    // Obfuscated-era Loom: remaps references (including mixin bytecode) to intermediary.
    id("net.fabricmc.fabric-loom-remap")
}

val minecraftVersion = property("deps.minecraft") as String
val loaderVersion = property("deps.fabric-loader") as String

group = property("mod.group") as String
version = "${property("mod.version")}+$minecraftVersion"
base.archivesName = property("mod.id") as String

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // Unified source namespace for the Stonecutter matrix: Mojang official mappings everywhere.
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // The mixin target audit needs the Minecraft class files at test runtime. Loom puts
    // them on the compile classpath but not on the test runtime classpath, so the path is
    // handed to the test explicitly (see MixinTargetAuditTest).
    doFirst {
        systemProperty("thorium.compileClasspath",
                configurations.getByName("compileClasspath").asPath)
    }
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

val templateValues = mapOf(
    "id" to property("mod.id").toString(),
    "name" to property("mod.name").toString(),
    "version" to project.version.toString(),
    "group" to property("mod.group").toString(),
    "author" to property("mod.author").toString(),
    "license" to property("mod.license").toString(),
    "description" to property("mod.description").toString(),
    "minecraft" to (project.findProperty("mod.mc_range") ?: minecraftVersion).toString(),
    "java_version" to "21",
    "mixin_legacy" to ",\n    \"legacy.LegacyMthMixin\",\n    \"legacy.LegacyOptionsScreenMixin\",\n    \"legacy.LegacyPauseScreenMixin\",\n    \"legacy.LegacyTitleScreenMixin\",\n    \"legacy.LegacyLevelRendererFrameMixin\",\n    \"legacy.LegacyFramePacingMixin\",\n    \"legacy.LegacyEntityCullingMixin\",\n    \"legacy.LegacyBlockEntityCullingMixin\",\n    \"legacy.LegacyFogMixin\",\n    \"legacy.LegacyWeatherMixin\",\n    \"legacy.LegacyKeyboardMixin\",\n    \"legacy.LegacyHudBatchingMixin\",\n    \"legacy.LegacyShadowMixin\",\n    \"legacy.LegacyChunkUploadBudgetMixin\",\n    \"legacy.LegacyCloudsMixin\",\n    \"legacy.LegacyEntityLodMixin\",\n    \"legacy.LegacySoundEngineMixin\",\n    \"legacy.LegacyTextureAnimationMixin\"",
    "mixin_modern" to ""
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(templateValues)
    filesMatching(listOf("fabric.mod.json", "thorium.mixins.json")) {
        expand(templateValues)
    }
}
