plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.iyanel"
version = "0.10.91"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Bundled storage drivers. Versions pinned to the last Java-8-compatible releases:
    // HikariCP 5.x needs Java 11, so stay on 4.0.3.
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("com.mysql:mysql-connector-j:8.4.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    // Thin jar kept as an intermediate; the shaded shadowJar owns the BedlamCore-<version>.jar deploy name.
    archiveClassifier.set("plain")
}

// Shade the storage drivers into the deploy jar (keeps the BedlamCore-<version>.jar name).
tasks.shadowJar {
    archiveFileName.set("BedlamCore-${project.version}.jar")
}
tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register<JavaExec>("coreCheck") {
    group = "verification"
    description = "Runs the dependency-free game rule checks."
    // compileOnly Spigot is needed so CosmeticsService/BedlamCore can classload; checks stay Bukkit-free.
    classpath = sourceSets.main.get().runtimeClasspath + configurations.compileClasspath.get()
    mainClass.set("dev.iyanel.bedlamcore.game.GameRulesCheck")
}

tasks.check {
    dependsOn("coreCheck")
}
