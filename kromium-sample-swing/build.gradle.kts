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

val jvmFlags: List<String> = listOf(
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
)

application {
    mainClass.set("dev.daviante.kromium.sample.swing.KromiumSwingApp")
    applicationDefaultJvmArgs = jvmFlags
}

tasks.named<JavaExec>("run") {
    jvmArgs(jvmFlags)
    if (System.getProperty("os.name")?.lowercase()?.contains("mac") == true) {
        jvmArgs("-Xdock:name=Kromium Swing", "-Dapple.awt.application.name=Kromium Swing")
    }
}
