plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.idea.webviewer"
version = "1.0.5"

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellij {
    version.set("2024.1")
    type.set("IC")
}

dependencies {
    // 仅用于单元测试；运行期依赖全部来自 IntelliJ 平台
    testImplementation("junit:junit:4.13.2")
}

tasks {
    test {
        useJUnit()
    }

    patchPluginXml {
        // 263 起 org.cef.* 才从平台核心 jar 移到独立的 com.intellij.modules.jcef 插件，
        // 也才存在这个模块 id；241~262 既没有该 id（硬依赖装不上），263 又必须要它（否则类加载失败）
        sinceBuild.set("263")
        untilBuild.set("263.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    instrumentCode {
        enabled = false
    }

    instrumentTestCode {
        enabled = false
    }
}