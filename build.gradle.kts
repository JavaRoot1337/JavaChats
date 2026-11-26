plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.2.2"
}

group = "ru.javaroot"
version = "2.0"

val targetJava = 21
val paperApiVersion: String by project
val modernPaperApiVersion: String by project
val bstatsVersion: String by project
val luckpermsApiVersion: String by project
val junitVersion: String by project

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))
    withSourcesJar()
}

val modernPaperApi by configurations.creating

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("net.luckperms:api:$luckpermsApiVersion")
    implementation("org.bstats:bstats-bukkit:$bstatsVersion")

    modernPaperApi("io.papermc.paper:paper-api:$modernPaperApiVersion")
    modernPaperApi("org.jetbrains:annotations:24.1.0")
    modernPaperApi("net.luckperms:api:$luckpermsApiVersion")
    modernPaperApi("org.bstats:bstats-bukkit:$bstatsVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("net.luckperms:api:$luckpermsApiVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitVersion")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJava)
        options.compilerArgs.add("-Xlint:all")
    }

    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        enabled = false
        archiveBaseName.set("JavaChats")
    }

    register<JavaCompile>("compileModernJava") {
        description = "Compiles the plugin sources against the modern Paper 26.x API."
        group = "verification"
        source = sourceSets.main.get().java
        classpath = modernPaperApi
        destinationDirectory.set(layout.buildDirectory.dir("classes/java/modern"))
    }

    check {
        dependsOn("compileModernJava")
    }

    shadowJar {
        archiveBaseName.set("JavaChats")
        archiveClassifier.set("")
        relocate("org.bstats", "ru.javaroot.javachats.libs.bstats")
        minimize()
    }

    build {
        dependsOn(shadowJar)
    }
}
