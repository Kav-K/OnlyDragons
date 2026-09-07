/**
 * Build two isolated lab source sets against a nontransitive legacy Bukkit API.
 * Java 8 bytecode keeps the harness/probe usable across the lab's supported server
 * range while the build itself uses the shared pinned JDK. Neither source set joins
 * the production IDE classpath. Explicit Jar tasks emit artifacts into root build.
 */
import java.util.Properties

plugins { java }

group = rootProject.group
val pins = Properties().apply { rootProject.file("versions.properties").inputStream().use { load(it) } }

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        content { includeGroup("org.spigotmc") }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(pins.getProperty("javaVersion").toInt()))
}

// A separate Gradle project keeps the legacy Bukkit API out of the plugin's IDE classpath.
for (toolName in listOf("labHarness", "labFixture")) {
    val source = sourceSets.create(toolName)
    dependencies.add(source.compileOnlyConfigurationName, "org.spigotmc:spigot-api:${pins.getProperty("labApiVersion")}") {
        isTransitive = false
    }
    tasks.named<JavaCompile>(source.compileJavaTaskName) {
        options.encoding = "UTF-8"
        options.release.set(8)
    }
    tasks.register<Jar>("${toolName}Jar") {
        from(source.output)
        archiveFileName.set(if (toolName == "labHarness") "${rootProject.name}Harness.jar" else "CompatibilityProbe.jar")
        destinationDirectory.set(rootProject.layout.buildDirectory.dir("lab-tools"))
    }
}
