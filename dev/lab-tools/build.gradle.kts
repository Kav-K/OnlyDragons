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
