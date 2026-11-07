plugins {
    id("java-library")
    id("maven-publish")
}

group = "info.cho"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val asmVersion = "9.11.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    implementation("io.github.classgraph:classgraph:4.8.181")

    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "info.cho.universalMixin.agent.MixinAgent")
    }
}

val agentJar = tasks.register<Jar>("agentJar") {
    description = "Builds a standalone Java agent including its runtime dependencies."
    group = "build"
    archiveClassifier = "agent"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.from(tasks.jar.get().manifest)
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude(
        "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA",
        "module-info.class", "META-INF/versions/**/module-info.class"
    )
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifactId = "universal-mixin"
            artifact(agentJar)
            pom {
                name = "UniversalMixin"
                description = "A small Java library and agent for launch-time mixins."
            }
        }
    }
    repositories {
        maven {
            name = "localBuild"
            url = uri(layout.buildDirectory.dir("repository"))
        }
    }
}

tasks.register<Sync>("prepareLibraryExample") {
    description = "Prepares an independent Gradle project using only the bundled library JAR."
    group = "application"
    into(layout.buildDirectory.dir("library-example"))
    from("examples/basic")
    from(agentJar) {
        into("libs")
        rename { "universal-mixin.jar" }
    }
}

val example = sourceSets.create("example")

val exampleMod = sourceSets.create("exampleMod") {
    compileClasspath += sourceSets.main.get().output + example.output
}

val exampleJar = tasks.register<Jar>("exampleJar") {
    description = "Builds the unmodified executable example application."
    group = "build"
    archiveClassifier = "example"
    from(example.output)
    manifest {
        attributes("Main-Class" to "info.cho.universalMixin.example.ExampleMain")
    }
}

val exampleModJar = tasks.register<Jar>("exampleModJar") {
    description = "Builds the separate mod JAR containing player mixins."
    group = "build"
    archiveClassifier = "example-mod"
    from(exampleMod.output)
}

tasks.assemble {
    dependsOn(agentJar, exampleJar, exampleModJar)
}

tasks.register<Exec>("runExample") {
    description = "Launches the executable example JAR with a separate mod JAR."
    group = "application"
    dependsOn(agentJar, exampleJar, exampleModJar)
    doFirst {
        val java = javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.absolutePath
        val agent = agentJar.get().archiveFile.get().asFile.absolutePath
        val mod = exampleModJar.get().archiveFile.get().asFile.absolutePath
        val application = exampleJar.get().archiveFile.get().asFile.absolutePath
        commandLine(java, "-javaagent:$agent=$mod", "-jar", application)
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(agentJar, exampleJar, exampleModJar)
    systemProperty("agent.jar", agentJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("example.jar", exampleJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("example.mod.jar", exampleModJar.get().archiveFile.get().asFile.absolutePath)
}
