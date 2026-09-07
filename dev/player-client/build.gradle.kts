/**
 * Standalone headless protocol client, never a production plugin dependency.
 * installDist stages the executable client and its strictly locked transitive graph;
 * the runner additionally verifies the exact timestamped protocol module's SHA-256.
 * Resource metadata carries the artifact/Minecraft/protocol identity into receipts.
 * No client credentials or human Minecraft profile are needed for disposable tests.
 */
import org.gradle.api.artifacts.dsl.LockMode
import java.util.Properties

plugins { application }

group = "com.kaveenk.onlydragons"
version = "1.0.0"
val projectPins = Properties().apply {
    file("../../versions.properties").inputStream().use { load(it) }
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-snapshots/") {
        content { includeGroup("org.geysermc.mcprotocollib") }
    }
    maven("https://repo.opencollab.dev/maven-releases/") {
        content { includeGroupByRegex("org\\.cloudburstmc.*"); includeGroupByRegex("com\\.nukkitx.*") }
    }
}

// This timestamped publication is the 26.2 branch, protocol 776. Main targets 26.1.
dependencies {
    implementation(projectPins.getProperty("testPlayerProtocolLib"))
    implementation("com.google.code.gson:gson:2.11.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.9")
    testImplementation(platform("org.junit:junit-bom:${projectPins.getProperty("junitVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(projectPins.getProperty("javaVersion").toInt())) }
application { mainClass.set("com.kaveenk.onlydragons.playerclient.ProtocolPlayer") }
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
    // Gradle normalizes a timestamped Maven publication to 26.2-SNAPSHOT in lock
    // state, which conflicts with our exact timestamp request on the next build.
    // This single module stays fixed by the declaration and verified SHA256;
    // its entire transitive graph remains subject to strict dependency locking.
    ignoredDependencies.add("org.geysermc.mcprotocollib:protocol")
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(projectPins.getProperty("javaVersion").toInt())
}
tasks.processResources {
    val values = mapOf("artifact" to projectPins.getProperty("testPlayerProtocolLib"),
                      "minecraftVersion" to projectPins.getProperty("minecraftVersion"),
                      "protocolVersion" to projectPins.getProperty("testPlayerProtocolVersion"))
    inputs.properties(values)
    filesMatching("player-client.properties") { expand(values) }
}
tasks.test { useJUnitPlatform() }
tasks.jar { archiveFileName.set("OnlyDragonsPlayerClient.jar") }
