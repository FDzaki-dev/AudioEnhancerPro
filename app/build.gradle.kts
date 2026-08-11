plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Batch 18: Hilt DI. `kapt` dipakai (bukan KSP) — annotation processor Hilt resmi
    // paling teruji di kombinasi Kotlin 1.9.24 ini, KSP support Hilt butuh setup versi
    // KSP terpisah yang harus persis cocok Kotlin version, risiko mismatch lebih tinggi
    // tanpa compiler buat verifikasi.
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.audioenhancer.booster"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.audioenhancer.booster"
        minSdk = 24
        targetSdk = 34
        versionCode = 81
        versionName = "1.80.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Hanya pakai signing config release kalau env var-nya tersedia (mis. di CI).
            // Kalau build lokal tanpa keystore, Gradle akan fallback tanpa signing (APK unsigned).
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
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
        // Batch 41: BuildConfig class TIDAK PERNAH dipakai di project ini (grep
        // `BuildConfig` di seluruh app/src/main/java: 0 hasil) — matiin generate-nya
        // skip task `generate{Debug,Release}BuildConfig` sepenuhnya, aman 100% karena
        // memang nol pemanggil, bukan cuma "kelihatannya nol".
        buildConfig = false
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Batch 18: rekomendasi resmi dokumentasi Hilt — tanpa ini, error di komponen yang
// digenerate Hilt kadang muncul sebagai error type asing yang membingungkan alih-alih
// pesan error yang jelas.
kapt {
    correctErrorTypes = true
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Batch 17: BoosterViewModel (AndroidViewModel) + `by viewModels()` di MainActivity.
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // Batch 18: Hilt DI.
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}
