plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.idea.webviewer"
version = "1.0.7"

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
    // 浠呯敤浜庡崟鍏冩祴璇曪紱杩愯鏈熶緷璧栧叏閮ㄦ潵鑷?IntelliJ 骞冲彴
    testImplementation("junit:junit:4.13.2")
}

tasks {
    test {
        useJUnit()
    }

    patchPluginXml {
        // 263 璧?org.cef.* 鎵嶄粠骞冲彴鏍稿績 jar 绉诲埌鐙珛鐨?com.intellij.modules.jcef 鎻掍欢锛?
        // 涔熸墠瀛樺湪杩欎釜妯″潡 id锛?41~262 鏃㈡病鏈夎 id锛堢‖渚濊禆瑁呬笉涓婏級锛?63 鍙堝繀椤昏瀹冿紙鍚﹀垯绫诲姞杞藉け璐ワ級
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