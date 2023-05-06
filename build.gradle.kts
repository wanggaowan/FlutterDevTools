plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.10.1"
}

group = "com.wanggaowan"
// version = "1.0-SNAPSHOT"
version = "1.8"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    // version.set("2022.3.1")
    // type.set("IC") // IC:intellij社区版 IU:intellij收费版

    version.set("221.6008.13.2211.9619390")
    type.set("AI") // AndroidStudio
    plugins.set(listOf("java","Kotlin","Dart:221.6103.1","io.flutter:72.1.2","yaml","com.localizely.flutter-intl:1.18.1-2020.3"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        sinceBuild.set("201")
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
