plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = "com.githeatmap"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")

    intellijPlatform {
        intellijIdea("2026.1")
        bundledPlugin("Git4Idea")
        jetbrainsRuntime()
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
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
