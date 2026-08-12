plugins {
    java
}

group = "dev.iyanel"
version = "0.7.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
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
    archiveFileName.set("BedlamCore-${project.version}.jar")
}

tasks.register<JavaExec>("coreCheck") {
    group = "verification"
    description = "Runs the dependency-free game rule checks."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.iyanel.bedlamcore.game.GameRulesCheck")
}

tasks.check {
    dependsOn("coreCheck")
}
