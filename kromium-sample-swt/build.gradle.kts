plugins {
    kotlin("jvm")
}

val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val isArm = osArch == "aarch64" || osArch == "arm64"

val swtArtifact = when {
    osName.contains("win") -> "org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.128.0"
    osName.contains("mac") -> if (isArm) "org.eclipse.platform:org.eclipse.swt.cocoa.macosx.aarch64:3.128.0" else "org.eclipse.platform:org.eclipse.swt.cocoa.macosx.x86_64:3.128.0"
    osName.contains("linux") -> if (isArm) "org.eclipse.platform:org.eclipse.swt.gtk.linux.aarch64:3.128.0" else "org.eclipse.platform:org.eclipse.swt.gtk.linux.x86_64:3.128.0"
    else -> "org.eclipse.platform:org.eclipse.swt.cocoa.macosx.aarch64:3.128.0"
}

dependencies {
    implementation(project(":kromium-core"))
    
    implementation(swtArtifact) {
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
    }
}

tasks.register<JavaExec>("run") {
    mainClass.set("dev.daviante.kromium.sample.swt.KromiumSwtApp")
    classpath = sourceSets["main"].runtimeClasspath
    if (osName.contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

