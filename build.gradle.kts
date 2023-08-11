plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "com.wanggaowan"
// version = "1.0-SNAPSHOT"
version = "2.2"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    // version.set("2022.3.1")
    // type.set("IC") // IC:intellij社区版 IU:intellij收费版

    // version.set("223.8836.35.2231.10406996")
    type.set("AI") // AndroidStudio
    // 配置本地已下载IDE路径，具体配置文档查看：https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#configuration-intellij-extension
    localPath.set("/Users/wgw/Documents/project/IdeaProjects/dev_ide/android studio/223.8836.35.2231.10406996/Android Studio.app/Contents")
    // Git4Idea: git插件
    plugins.set(
        listOf(
            "java",
            "Kotlin",
            "Dart:223.8950",
            "io.flutter:74.0.3",
            "yaml"
        )
    )

    // 是否开启增量构建
    instrumentCode.set(true)
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("10000.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
