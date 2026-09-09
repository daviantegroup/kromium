plugins {
    `java`
    application
}

val javafxVersion = "21.0.5"
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val isArm = osArch == "aarch64" || osArch == "arm64"

val platformClassifier = when {
    osName.contains("win") -> if (isArm) "win-aarch64" else "win"
    osName.contains("mac") -> if (isArm) "mac-aarch64" else "mac"
    osName.contains("linux") -> if (isArm) "linux-aarch64" else "linux"
    else -> "linux"
}

dependencies {
    implementation(project(":kromium-core"))

    listOf("base", "graphics", "controls", "swing").forEach { module ->
        implementation("org.openjfx:javafx-$module:$javafxVersion:$platformClassifier")
    }
}

application {
    mainClass.set("dev.daviante.kromium.sample.javafx.KromiumJavaFxLauncher")
}