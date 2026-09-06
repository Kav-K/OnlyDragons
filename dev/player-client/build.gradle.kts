import org.gradle.api.artifacts.dsl.LockMode

plugins { application }

group = "com.kaveenk.onlydragons"
version = "1.0.0"

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
    implementation("org.geysermc.mcprotocollib:protocol:26.2-20260824.124638-17")
    implementation("com.google.code.gson:gson:2.11.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.9")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
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
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(25) }
tasks.test { useJUnitPlatform() }
tasks.jar { archiveFileName.set("OnlyDragonsPlayerClient.jar") }
