import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = "com.githeatmap"
version = "1.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")

    intellijPlatform {
        intellijIdea("2026.1")
        bundledPlugin("Git4Idea")
        jetbrainsRuntime()
    }
}

tasks.test {
    useJUnitPlatform()
    doFirst {
        layout.buildDirectory.dir("tmp/test").get().asFile.mkdirs()
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2")
            create(IntelliJPlatformType.IntellijIdea, "2024.2")
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
            create(IntelliJPlatformType.IntellijIdea, "2024.3")
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            create(IntelliJPlatformType.IntellijIdea, "2025.1")
            create(IntelliJPlatformType.IntellijIdea, "2026.1")
        }
    }
}

kover {
    reports {
        total {
            filters {
                includes {
                    packages(
                        "com.githeatmap.cache",
                        "com.githeatmap.engine",
                        "com.githeatmap.git"
                    )
                }
            }

            html {}
            xml {}

            verify {
                rule {
                    minBound(70)
                }
            }
        }
    }
}

tasks {
    check {
        dependsOn("koverVerify")
    }
}
