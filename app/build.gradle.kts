plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.matchday.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.matchday.tv"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"

        ndk {
            // Fire OS 7 sticks are 32-bit; Google TV devices may be arm64.
            // The x86_64 emulator ABI is added only to the debug variant below.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        // Release signing is opt-in. CI/local builds without a keystore stay debug-signed;
        // the public release workflow will supply these environment variables.
        val storePath = System.getenv("MATCHDAY_KEYSTORE")
        if (!storePath.isNullOrBlank() && rootProject.file(storePath).exists()) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = System.getenv("MATCHDAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MATCHDAY_KEY_ALIAS")
                keyPassword = System.getenv("MATCHDAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Prefer the real release key when provided; debug signing is only a local fallback.
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

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
        // Optional: -Pmatchday.abis=arm64-v8a trims packaged native libs for hardware that is
        // arm64-only (Sony/LG Google TVs), roughly halving the sideload size. The default
        // release keeps both arm ABIs for Fire OS 7 32-bit sticks.
        providers.gradleProperty("matchday.abis").orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { requested ->
                val keep = requested.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                listOf("armeabi-v7a", "arm64-v8a")
                    .filter { it !in keep }
                    .forEach { abi -> jniLibs.excludes += "**/lib/$abi/**" }
            }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // GPLv3 headless engine. Source: https://github.com/ahXN00/OwnTV_Core
    implementation(libs.owntv.core)
    implementation(libs.owntv.player.core)

    testImplementation(libs.junit)
}
