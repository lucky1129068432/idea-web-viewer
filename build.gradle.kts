plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.idea.webviewer"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellij {
    version.set("2023.3.6")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("261.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    instrumentCode {
        enabled = false
    }
}