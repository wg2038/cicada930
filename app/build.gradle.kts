plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.x.opusone"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.x.opusone"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val opusStoreFile = (findProperty("OPUSONE_STORE_FILE") as? String) ?: System.getenv("OPUSONE_STORE_FILE")
            val opusStorePassword = (findProperty("OPUSONE_STORE_PASSWORD") as? String) ?: System.getenv("OPUSONE_STORE_PASSWORD")
            val opusKeyAlias = (findProperty("OPUSONE_KEY_ALIAS") as? String) ?: System.getenv("OPUSONE_KEY_ALIAS")
            val opusKeyPassword = (findProperty("OPUSONE_KEY_PASSWORD") as? String) ?: System.getenv("OPUSONE_KEY_PASSWORD")
            signingConfig = if (listOf(opusStoreFile, opusStorePassword, opusKeyAlias, opusKeyPassword).all { !it.isNullOrBlank() }) {
                signingConfigs.findByName("release")?.apply {
                    storeFile = file(opusStoreFile!!)
                    storePassword = opusStorePassword!!
                    keyAlias = opusKeyAlias!!
                    keyPassword = opusKeyPassword!!
                } ?: signingConfigs.create("release") {
                    storeFile = file(opusStoreFile!!)
                    storePassword = opusStorePassword!!
                    keyAlias = opusKeyAlias!!
                    keyPassword = opusKeyPassword!!
                }
            } else {
                logger.warn(
                    "[Cicada] Missing OPUSONE_STORE_* credentials; release build falling back to debug signing."
                )
                signingConfigs.getByName("debug")
            }
        }

        // 性能评估构建变体：基于 debug 配置，关闭 isDebuggable 以支持 AOT 编译
        create("perf") {
            initWith(getByName("debug"))
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  // XML 侧 M3 主题（启动窗口 DayNight 主题，Compose 渲染层不受影响）
  implementation(libs.android.material)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Local tests: jUnit, coroutines
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
