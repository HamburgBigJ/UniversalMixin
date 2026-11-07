plugins {
    application
}

val mixinJar = layout.projectDirectory.file("libs/universal-mixin.jar")

dependencies {
    implementation(files(mixinJar))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

application {
    mainClass = "example.Main"
    applicationDefaultJvmArgs = listOf("-javaagent:${mixinJar.asFile.absolutePath}")
}
