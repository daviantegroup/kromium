plugins {
    `java`
    application
}

dependencies {
    implementation(project(":kromium-core"))
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("com.formdev:flatlaf-intellij-themes:3.5.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("dev.daviante.kromium.sample.swing.KromiumSwingApp")
}

val osName = System.getProperty("os.name")?.lowercase() ?: ""
val iconIcns = file("src/main/resources/icon.icns")

tasks.named<JavaExec>("run") {
    if (osName.contains("mac")) {
        jvmArgs("-Xdock:name=Kromium")
        if (iconIcns.exists()) {
            jvmArgs("-Xdock:icon=${iconIcns.absolutePath}")
        }
        jvmArgs("-Dapple.awt.application.name=Kromium")
    }
}
