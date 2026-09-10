plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("io.ktor:ktor-client-core:3.1.0")
    implementation("io.ktor:ktor-client-okhttp:3.1.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("io.ktor:ktor-client-mock:3.1.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.named<Jar>("jar") {
    from(zipTree("libs/jcef.jar")) {
        exclude("META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
