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
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("dev.daviante.kromium.sample.swing.KromiumSwingApp")
}

tasks.named<JavaExec>("run") {
    if (System.getProperty("os.name")?.lowercase()?.contains("mac") == true) {
        jvmArgs("-Xdock:name=Kromium Swing", "-Dapple.awt.application.name=Kromium Swing")
    }
}
