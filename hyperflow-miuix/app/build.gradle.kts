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
        versionCode = 35
        versionName = "0.4.0"
    }

    signingConfigs {
        create("release") {
            // keystore 由 CI 注入（workflow 解码写入 hyperflow-miuix/keystore.jks），本地无文件则回退 debug 签名
            val kf = File("${rootProject.projectDir}/keystore.jks")
            if (kf.exists()) {
                storeFile = kf
                storePassword = System.getenv("HF_KEY_PASS") ?: "hyperflowplus"
                keyAlias = System.getenv("HF_KEY_ALIAS") ?: "hyperflowplus"
                keyPassword = System.getenv("HF_KEY_PASS") ?: "hyperflowplus"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
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
    implementation("androidx.activity:activity-compose:1.9.3")

    // Miuix（HyperOS 风格）核心 / 设置项 / 图标
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")

    // Miuix 模糊（柔光玻璃，minSdk 33）
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")

    // LSPosed libxposed API（仅编译期，运行时由框架提供；坐标源不稳定，内置 stub 模块）
    compileOnly(project(":libxposed-stub"))

    implementation("org.json:json:20240303")
}
