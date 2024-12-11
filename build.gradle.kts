plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "com.wanggaowan"
// version = "1.0-SNAPSHOT"
version = "4.0"

repositories {
    maven { setUrl("https://maven.aliyun.com/repository/central") }
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    // version.set("2022.3.1")
    // type.set("IC") // IC:intellij社区版 IU:intellij收费版

    // version.set("223.8836.35.2231.10406996")
    type.set("AI") // AndroidStudio
    // 配置本地已下载IDE路径，具体配置文档查看：https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#configuration-intellij-extension
    localPath.set("/Users/wgw/Documents/develop/project/ide plugin/test ide/AndroidStudio241.app/Contents")
    // Git4Idea: git插件
    // org.jetbrains.android: Android插件
    plugins.set(
        listOf(
            "java",
            "Kotlin",
            "Dart:241.18808",
            "io.flutter:83.0.2",
            "yaml",
            "Git4Idea",
        )
    )

    // 是否开启增量构建
    instrumentCode.set(false)
}

// dependencies {
//     implementation("com.aliyun:alimt20181012:1.2.0") {
//         exclude(group="org.slf4j")
//     }
// }

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("243")
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

    instrumentCode {
        compilerVersion.set("241.15989.150")
    }
}
