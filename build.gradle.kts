import java.util.Properties
import java.util.zip.ZipFile

plugins {
    java
    jacoco
}

group = "com.kaveenk.onlydragons"
version = "0.1.0-SNAPSHOT"

val pins = Properties().apply { file("versions.properties").inputStream().use { load(it) } }
fun pin(name: String): String = requireNotNull(pins.getProperty(name)) { "Missing version: $name" }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(pin("javaVersion").toInt()))
    withSourcesJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${pin("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${pin("paperApiVersion")}")
    testImplementation("org.mockbukkit.mockbukkit:${pin("mockBukkitArtifact")}:${pin("mockBukkitVersion")}")
    testImplementation(platform("org.junit:junit-bom:${pin("junitVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(pin("javaVersion").toInt())
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val values = mapOf("version" to project.version, "apiVersion" to pin("minecraftVersion"))
    inputs.properties(values)
    filesMatching("plugin.yml") { expand(values) }
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
    testLogging { events("passed", "skipped", "failed") }
    // MockBukkit aborts tests when an API is unimplemented. Do not silently count them as passing.
    doLast {
        val skipped = Regex("""<testsuite\b[^>]*\bskipped="([1-9]\d*)"""")
        reports.junitXml.outputLocation.get().asFile.listFiles { f -> f.extension == "xml" }?.forEach {
            check(!skipped.containsMatchIn(it.readText())) { "Skipped tests in ${it.name}; inspect MockBukkit support." }
        }
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco { toolVersion = pin("jacocoVersion") }
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}

// Let the lab follow the actual archive name when project name or version changes.
val writePluginArtifact = tasks.register("writePluginArtifact") {
    dependsOn(tasks.jar)
    doLast {
        layout.buildDirectory.file("plugin-artifact.txt").get().asFile
            .writeText(tasks.jar.get().archiveFile.get().asFile.absolutePath)
    }
}
tasks.build { dependsOn(writePluginArtifact) }

// Keep server-testing tools in a separate IDE project: their legacy Bukkit API must not
// shadow Paper's classes when the editor merges a project's source-set classpaths.
for (toolName in listOf("labHarness", "labFixture")) {
    tasks.register("${toolName}Jar") {
        group = "minecraft"
        dependsOn(":lab-tools:${toolName}Jar")
    }
}

val verifyApiIsolation = tasks.register("verifyApiIsolation") {
    group = "verification"
    description = "Reject competing Bukkit APIs in the plugin's merged IDE classpath."
    val editorClasspath = files(provider { sourceSets.map { it.compileClasspath } })
    inputs.files(editorClasspath)
    doLast {
        val providers = editorClasspath.files.filter { candidate ->
            candidate.isFile && candidate.extension == "jar" && ZipFile(candidate).use {
                it.getEntry("org/bukkit/command/CommandSender.class") != null
            }
        }
        check(providers.size == 1 && providers.single().name.startsWith("paper-api-")) {
            "Expected only Paper's Bukkit API on the plugin IDE classpath, found: $providers"
        }
    }
}
tasks.check { dependsOn(verifyApiIsolation) }
tasks.build { dependsOn("labHarnessJar", "labFixtureJar") }

// Windows server tasks delegate to the same version-isolated pipeline as Cursor and mcdev.cmd.
for ((taskName, action) in mapOf("prepareServer" to "prepare", "deployPlugin" to "prepare", "runServer" to "run")) {
    tasks.register<Exec>(taskName) {
        group = "minecraft"
        description = "Use the plugin lab to $action the pinned server."
        dependsOn(tasks.build, "labHarnessJar")
        val arguments = mutableListOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                file("scripts/Lab.ps1").absolutePath, action, "-SkipBuild")
        if (providers.gradleProperty("debugServer").isPresent) arguments.add("-DebugServer")
        commandLine(arguments)
    }
}
