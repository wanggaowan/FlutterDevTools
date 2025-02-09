plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

repositories {
    maven { setUrl("https://maven.aliyun.com/repository/central") }
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

// 新版本的配置文件：https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform {
    buildSearchableOptions = false
    // 是否开启增量构建
    instrumentCode = false

    pluginConfiguration {
        group = "com.wanggaowan"
        name = "FlutterDevTools"
        version = "4.1"

        ideaVersion {
            sinceBuild = "242"
            untilBuild = "10000.*"
        }
    }

    // publishing {
    //     // 用于发布插件的主机名,默认值https://plugins.jetbrains.com
    //     host = ""
    //     // 发布需要的秘钥
    //     token = "7hR4nD0mT0k3n_8f2eG"
    //     // 要将插件上传到的频道名称列表
    //     channels = listOf("default")
    //     // 指定是否应使用 IDE 服务插件存储库服务。
    //     ideServices = false
    //     // 发布插件更新并将其标记为隐藏，以防止在批准后公开可见。
    //     hidden = false
    // }

    // signing {
    //     cliPath = file("/path/to/marketplace-zip-signer-cli.jar")
    //     keyStore = file("/path/to/keyStore.ks")
    //     keyStorePassword = "..."
    //     keyStoreKeyAlias = "..."
    //     keyStoreType = "..."
    //     keyStoreProviderName = "..."
    //     privateKey = "..."
    //     privateKeyFile = file("/path/to/private.pem")
    //     password = "..."
    //     certificateChain = "..."
    //     certificateChainFile = file("/path/to/chain.crt")
    // }
}

dependencies {
    intellijPlatform {
        // androidStudio("2024.2.2", useInstaller = true)
        local("/Users/wgw/Documents/develop/project/ide plugin/test ide/Android Studio.app")
        bundledPlugins("org.jetbrains.kotlin","org.jetbrains.plugins.yaml", "Git4Idea")
        plugins("Dart:242.24931", "io.flutter:83.0.3")
    }

    //     implementation("com.aliyun:alimt20181012:1.2.0") {
    //         exclude(group="org.slf4j")
    //     }
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
}
