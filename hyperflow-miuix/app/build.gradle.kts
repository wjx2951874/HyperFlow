plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hyperflowplus"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hyperflowplus"
        minSdk = 33          // miuix-blur 硬性要求（RuntimeShader）
        targetSdk = 35
        versionCode = 124
        versionName = "0.6.16.8"
    }

    val hfKeystore = File("${rootProject.projectDir}/keystore.jks")
    signingConfigs {
        if (hfKeystore.exists()) {
            create("release") {
                storeFile = hfKeystore
                storePassword = System.getenv("HF_KEY_PASS")?.takeIf { it.isNotEmpty() } ?: "hyperflowplus"
                keyAlias = System.getenv("HF_KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "hyperflowplus"
                keyPassword = System.getenv("HF_KEY_PASS")?.takeIf { it.isNotEmpty() } ?: "hyperflowplus"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Compose BOM（Miuix 依赖的基础 Compose UI）
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    // v0.5.13：InstallerX 同款状态卡图标（Rounded.CheckCircleOutline / ErrorOutline 圆环家族，
    // Google Material Icons 官方开源库 Apache-2.0；R8 会裁剪未使用图标，包体增量很小）
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.9.3")
    // v0.5.12：FileProvider（分享运行日志文件）
    implementation("androidx.core:core-ktx:1.13.1")

    // Miuix（HyperOS 风格）核心 / 设置项 / 图标
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")

    // Miuix 模糊（柔光玻璃，minSdk 33）
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")


    // LSPosed libxposed API（compileOnly；官方 Maven 坐标，与 LSPosed 2.2.x 运行时混淆签名一致）
    // libxposed service：App 侧绑定 LSPosed daemon，秒级实时获取框架连接与模块作用域（参考 HyperModifier）
    implementation("io.github.libxposed:service:102.0.0")
    compileOnly("io.github.libxposed:api:102.0.0")

    implementation("org.json:json:20240303")
}
