plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":kromium-compose"))
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
}

kotlin {
    jvmToolchain(17)
}

val osName = System.getProperty("os.name")?.lowercase() ?: ""
val iconIcns = file("src/main/resources/icon.icns")
val jvmOpens = buildList {
    if (osName.contains("mac")) {
        add("-Xdock:name=Kromium")
        if (iconIcns.exists()) {
            add("-Xdock:icon=${iconIcns.absolutePath}")
        }
        add("-Dapple.awt.application.name=Kromium")
    }
}

compose.desktop {
    application {
        mainClass = "dev.daviante.kromium.demo.MainKt"
        jvmArgs += jvmOpens
        nativeDistributions {
            packageName = "Kromium"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
                bundleID = "dev.daviante.kromium.sample.compose"
                dockName = "Kromium"
            }
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

