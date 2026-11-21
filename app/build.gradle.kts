import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.gradle)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val keystoreProperties = Properties().apply {
    val localFile = rootProject.file("keystore.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun propOrEnv(key: String, env: String): String? =
    keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStorePath = propOrEnv("storeFile", "NAIRO_STORE_FILE") ?: "nairo-release.jks"
val releaseStoreFile = rootProject.file(releaseStorePath)
val releaseStorePassword = propOrEnv("storePassword", "NAIRO_STORE_PASSWORD")
val releaseKeyAlias = propOrEnv("keyAlias", "NAIRO_KEY_ALIAS") ?: "nairo"
val releaseKeyPassword = propOrEnv("keyPassword", "NAIRO_KEY_PASSWORD")
val hasReleaseSigning =
    releaseStoreFile.exists() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

fun isReleaseAssembleRequested(): Boolean =
    gradle.startParameter.taskNames.any { name ->
        val n = name.lowercase()
        (n.contains("assemblerelease") || n.contains("bundlerelease") || n.endsWith("release")) &&
            !n.contains("unittest") &&
            !n.contains("androidtest")
    }

// 仅在真正打正式包时强制签名；debug / unitTest 不要求口令
if (isReleaseAssembleRequested() && !hasReleaseSigning) {
    error(
        "缺少正式签名：需要 nairo-release.jks + NAIRO_STORE_PASSWORD / NAIRO_KEY_PASSWORD" +
            "（本地 keystore.properties 或 CI Secrets）",
    )
}

android {
    namespace = "com.nairo.app"
    // API 37 仅以 platforms;android-37.0 发布，必须显式 minorApiLevel
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.nairo.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 24
        versionName = "1.4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // 禁止回落 debug；口令在配置阶段已按任务校验
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM 单测需要真实 org.json；android.jar 里是 stub，会抛 “not mocked”
    testImplementation(libs.org.json)
}
