plugins {
    // Unobfuscated-era Loom: no mappings, no remapping, plain jars.
    id("net.fabricmc.fabric-loom")
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
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

val templateValues = mapOf(
    "id" to property("mod.id").toString(),
    "name" to property("mod.name").toString(),
    "version" to property("mod.version").toString(),
    "group" to property("mod.group").toString(),
    "author" to property("mod.author").toString(),
    "license" to property("mod.license").toString(),
    "description" to property("mod.description").toString(),
    "minecraft" to "*",
    "mixin_legacy" to "",
    "mixin_modern" to ",\n    \"modern.ModernMthMixin\",\n    \"modern.ModernOptionsScreenMixin\",\n    \"modern.ModernPauseScreenMixin\",\n    \"modern.ModernTitleScreenMixin\",\n    \"modern.ModernKeyboardMixin\",\n    \"modern.ModernHudBatchingMixin\",\n    \"modern.ModernFramePacingMixin\",\n    \"modern.ModernEntityCullingMixin\",\n    \"modern.ModernBlockEntityCullingMixin\",\n    \"modern.ModernFogMixin\",\n    \"modern.ModernWeatherMixin\",\n    \"modern.ModernShadowMixin\",\n    \"modern.ModernCloudsMixin\",\n    \"modern.ModernEntityLodMixin\",\n    \"modern.ModernSoundEngineMixin\",\n    \"modern.ModernTextureAnimationMixin\""
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(templateValues)
    filesMatching(listOf("fabric.mod.json", "thorium.mixins.json")) {
        expand(templateValues)
    }
}
