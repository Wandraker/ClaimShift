plugins {
    java
}

group = "dev.onelsey"
version = "1.1.1"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "enginehub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.116-stable")

    // EngineHub modules are API-only here. Their transitive Mojang Guava/Gson
    // constraints intentionally stay out of Paper 26.2's compile classpath.
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.18") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.4") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.4") { isTransitive = false }

    compileOnly("com.github.angeschossen:LandsAPI:7.25.4") { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("ClaimShift")
    archiveVersion.set(project.version.toString())
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "ClaimShift-LICENSE.txt" }
    }
    manifest {
        attributes(
            "Implementation-Title" to "ClaimShift",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Onelsey"
        )
    }
}

tasks.named<Jar>("sourcesJar") {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
