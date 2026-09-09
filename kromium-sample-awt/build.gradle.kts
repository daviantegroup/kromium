plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":kromium-core"))
}

tasks.register<JavaExec>("run") {
    mainClass.set("dev.daviante.kromium.sample.awt.KromiumAwtApp")
    classpath = sourceSets["main"].runtimeClasspath
}
