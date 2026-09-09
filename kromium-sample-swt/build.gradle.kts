plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":kromium-core"))
    
    // Eclipse SWT dependency (Windows x64 default for sample purposes)
    implementation("org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.128.0") {
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
    }
}

tasks.register<JavaExec>("run") {
    mainClass.set("dev.daviante.kromium.sample.swt.KromiumSwtApp")
    classpath = sourceSets["main"].runtimeClasspath
}

