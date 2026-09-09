plugins {
    `java`
    application
}

val javafxVersion = "17.0.2"
val osName = System.getProperty("os.name").lowercase()
val platformClassifier = when {
    osName.contains("win") -> "win"
    osName.contains("mac") -> "mac"
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