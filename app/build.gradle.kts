plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.akshansh.aktv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.akshansh.aktv"
        minSdk = 21            // runs on Android TV 5.0 → 14 (your Android 9 and 13 TVs included)
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        // Optional values injected from GitHub Secrets (see README)
        val workerUrl = (project.findProperty("workerUrl") as String?)?.trim().orEmpty()
        val workerKey = (project.findProperty("workerKey") as String?)?.trim().orEmpty()
        buildConfigField("String", "WORKER_URL", "\"$workerUrl\"")
        buildConfigField("String", "WORKER_KEY", "\"$workerKey\"")
        buildConfigField(
            "String", "PLAYLIST_URL",
            "\"https://raw.githubusercontent.com/Indiblog/india-iptv/main/output/india_iptv.m3u\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the APK installs directly on your TVs.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("io.coil-kt:coil-compose:2.6.0")

    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
}
