/**
 * Isolated real-Paper companion build. Build the production root first: its artifact
 * receipt selects the exact plugin JAR used at compile time. Production and Paper
 * remain compile-only so the companion cannot package competing implementations.
 *
 * The runner separately verifies JAR hashes, scenario reports and process cleanup.
 * Passing this project's JUnit tests or javadoc does not establish Minecraft behavior.
 */
import java.util.Properties

plugins { java }
group = "com.kaveenk.onlydragons"
version = "1.0.0"
val pluginRoot = projectDir.resolve("../..").canonicalFile
val pins = Properties().apply { pluginRoot.resolve("versions.properties").inputStream().use { load(it) } }
val artifactReceipt = pluginRoot.resolve("build/plugin-artifact.txt")
require(artifactReceipt.isFile) { "Build OnlyDragons with the root wrapper before building game-tests." }
val pluginArtifact = file(artifactReceipt.readText().trim())
require(pluginArtifact.isFile) { "OnlyDragons artifact receipt points to a missing JAR; rebuild the root project." }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(pins.getProperty("javaVersion").toInt())) }
dependencies {
    compileOnly("io.papermc.paper:paper-api:${pins.getProperty("paperApiVersion")}")
    compileOnly(files(pluginArtifact))
    testImplementation(platform("org.junit:junit-bom:${pins.getProperty("junitVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(pins.getProperty("javaVersion").toInt())
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}
tasks.processResources {
    inputs.property("apiVersion", pins.getProperty("minecraftVersion"))
    filesMatching("plugin.yml") { expand("apiVersion" to pins.getProperty("minecraftVersion")) }
}
tasks.jar { archiveFileName.set("OnlyDragonsGameTests.jar") }

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "128m"
    doLast {
        val skipped = Regex("""<testsuite\b[^>]*\bskipped="([1-9]\d*)"""")
        reports.junitXml.outputLocation.get().asFile.listFiles { f -> f.extension == "xml" }?.forEach {
            check(!skipped.containsMatchIn(it.readText())) { "Skipped fixture tests in ${it.name}" }
        }
    }
}
