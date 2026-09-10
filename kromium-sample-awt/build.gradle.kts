plugins {
    kotlin("jvm")
}

val osName = System.getProperty("os.name")?.lowercase() ?: ""

dependencies {
    implementation(project(":kromium-core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.register<JavaExec>("run") {
    mainClass.set("dev.daviante.kromium.sample.awt.KromiumAwtApp")
    classpath = sourceSets["main"].runtimeClasspath
    if (osName.contains("mac")) {
        jvmArgs("-Xdock:name=Kromium AWT")
        jvmArgs("-Dapple.awt.application.name=Kromium AWT")
    }
}
