plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
    `maven-publish`
}

allprojects {
    group = "dev.daviante"
    version = "3.0.150-b11"

    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jogamp.org/deployment/maven")
    }
}

val publishedProjects = setOf("kromium-core", "kromium-compose")

subprojects {
    if (project.name in publishedProjects) {
        afterEvaluate {
            apply(plugin = "maven-publish")
            apply(plugin = "signing")

            val javadocJar by tasks.registering(Jar::class) {
                archiveClassifier.set("javadoc")
            }

            val sourcesJar by tasks.registering(Jar::class) {
                archiveClassifier.set("sources")
                val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
                if (sourceSets != null) {
                    from(sourceSets.getByName("main").allSource)
                }
            }

            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        artifact(sourcesJar)
                        artifact(javadocJar)

                        pom {
                            name.set(if (project.name == "kromium-core") "Kromium Core" else "Kromium Compose")
                            description.set(
                                if (project.name == "kromium-core")
                                    "Chromium Embedded Framework (CEF) library for Kotlin"
                                else
                                    "Compose Multiplatform Desktop integration for Kromium"
                            )
                            url.set("https://kromium.daviante.dev")
                            licenses {
                                license {
                                    name.set("The Apache License, Version 2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                }
                            }
                            developers {
                                developer {
                                    id.set("daviante")
                                    name.set("Daviante Group")
                                    email.set("code@daviante.dev")
                                    url.set("https://daviante.dev")
                                }
                            }
                            scm {
                                connection.set("scm:git:git://github.com/daviantegroup/kromium.git")
                                developerConnection.set("scm:git:ssh://github.com/daviantegroup/kromium.git")
                                url.set("https://kromium.daviante.dev")
                            }
                        }
                    }
                }

                repositories {
                    maven {
                        name = "staging"
                        url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
                    }
                }
            }

            configure<SigningExtension> {
                val signingKey = findProperty("signing.key")?.toString() ?: System.getenv("GPG_SIGNING_KEY")
                val signingPassword = findProperty("signing.password")?.toString() ?: System.getenv("GPG_SIGNING_PASSWORD")
                if (!signingKey.isNullOrBlank()) {
                    useInMemoryPgpKeys(signingKey, signingPassword ?: "")
                    sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
                }
            }
        }
    }
}

tasks.register<Zip>("bundlePublishing") {
    description = "Packages the staging repository into a zip bundle for Sonatype Central Portal upload"
    group = "publishing"
    dependsOn(subprojects.filter { it.name in publishedProjects }.map { it.tasks.named("publishAllPublicationsToStagingRepository") })
    from(layout.buildDirectory.dir("staging-deploy")) {
        include("dev/daviante/*/${project.version}/**")
    }
    archiveFileName.set("bundle.zip")
    destinationDirectory.set(layout.buildDirectory)
}

