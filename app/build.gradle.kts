plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Batch 142: detekt (analisis statis terfokus, NON-BLOCKING — lihat blok `detekt {}` di bawah &
    // config/detekt/detekt.yml). Versi dipasang di sini (bukan root build.gradle.kts) biar cuma 1
    // file build yang disentuh; Kotlin 1.9.24 sepaket dengan detekt 1.23.x (dibangun di atas Kotlin 1.9).
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    // Batch 49: kapt + Hilt (Batch 18) DICABUT — lihat CHANGELOG.md v1.86.0. kapt
    // (annotation processing) adalah kontributor waktu compile TERBESAR di project
    // ini, dan Hilt cuma dipakai buat 1 titik inject sepele (Application ke
    // BoosterViewModel) yang sudah didapat GRATIS dari `AndroidViewModel` bawaan
    // AndroidX (SavedStateViewModelFactory tahu cara construct AndroidViewModel
    // subclass via constructor(Application) TANPA DI framework apapun — mekanisme
    // ini sudah ada sejak awal library ViewModel, bukan fitur baru).
}

android {
    namespace = "com.audioenhancer.booster"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.audioenhancer.booster"
        minSdk = 24
        targetSdk = 34
        // Batch 76 (Versioning Lock, DIPERLUAS eksplisit oleh user — lihat
        // PROJECT_STATE.md "Keputusan sadar" utk riwayat lengkap kenapa dulu SENGAJA
        // dipisah): versionName SEKARANG IKUT otomatis dari GITHUB_RUN_NUMBER, SAMA
        // PERSIS sumbernya dengan versionCode di bawah — user eksplisit pilih opsi
        // "angka run number polos" (bukan semantic+suffix) saat ditanya. Konsekuensi
        // LANGSUNG: versionName BUKAN LAGI label semantik manusia (mis. "1.99.0")
        // — jadi CI step "Extract changelog entry for this version"
        // (.github/workflows/build.yml) TIDAK BISA LAGI cari header CHANGELOG.md
        // persis `## v<versionName>` (nilainya beda tiap run, mustahil ditebak
        // Claude SEBELUM push). Step itu DIREDESAIN batch ini jadi ambil section
        // PALING ATAS CHANGELOG.md apa adanya (konsisten sama konvensi
        // "entry terbaru paling atas" yang sudah dipakai file itu) — TIDAK lagi
        // cocokkan versi sama sekali. Heading CHANGELOG.md ke depan JUGA ganti
        // format (BUKAN lagi `## v<versionName> - Batch N: ...`, lihat CHANGELOG.md
        // + PROJECT_STATE.md Batch 76 utk detail).
        // Batch 65 (asal instruksi Versioning Lock): GITHUB_RUN_NUMBER env var
        // BAWAAN GitHub Actions (otomatis ada di tiap step, tanpa perlu di-`env:`
        // eksplisit di workflow), naik monoton per run workflow ini, TIDAK PERNAH
        // reset/reuse — kontrak ini PERSIS sama dengan syarat versionCode Android
        // (wajib strictly-increasing tiap APK yang dipublish). Fallback `"1"`/`1`
        // HANYA kepake kalau ke-evaluate di luar GitHub Actions (mis. lokal) — TIDAK
        // relevan buat rilis nyata, app ini SATU-SATUNYA dibuild lewat CI.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("GITHUB_RUN_NUMBER") ?: "1"
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
    // Batch 142: Android Lint NON-BLOCKING (`./gradlew lintDebug`). abortOnError=false → temuan
    // TIDAK menggagalkan build manapun (termasuk lintVitalRelease di assembleRelease); severity per
    // check diatur di app/lint.xml. checkReleaseBuilds & set "fatal" sengaja TIDAK disentuh biar
    // perilaku release tidak berubah sama sekali.
    lint {
        abortOnError = false
        warningsAsErrors = false
        checkDependencies = false
        lintConfig = file("lint.xml")
        htmlReport = true
        xmlReport = true
        textReport = true
        // Batch 164: SARIF (lint-results-debug.sarif) buat koreksi mandiri via koordinat uri/startLine, dan
        // `checkTestSources` supaya src/test ikut dilint. Keduanya cuma menambah laporan — tetap NON-BLOCKING.
        sarifReport = true
        checkTestSources = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Batch 142: detekt WHITELIST (buildUponDefaultConfig=false → hanya rule di config/detekt/detekt.yml
// yang aktif). ignoreFailures=true = NON-BLOCKING: temuan masuk laporan (app/build/reports/detekt/),
// tidak menggagalkan build. Scan HANYA src/main (test kecil, bukan sumber bug runtime).
detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
    allRules = false
    parallel = true
    ignoreFailures = true
    source.setFrom("src/main/java", "src/main/kotlin")
}

// Batch 164: jaring TAMBAHAN type-resolution. Task `detektDebug` (dibuat plugin detekt untuk varian debug,
// punya classpath) membaca detekt.yml + detekt-typed.yml; task `detekt` polos di atas TIDAK disentuh (tetap
// jaring lama yang sudah hijau). Tetap NON-BLOCKING lewat `ignoreFailures` extension di atas + step CI sendiri.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    if (name == "detektDebug") {
        jvmTarget = "17"
        config.setFrom(
            rootProject.file("config/detekt/detekt.yml"),
            rootProject.file("config/detekt/detekt-typed.yml")
        )
        // Batch 165: diagnostik NON-BLOCKING. Canary UnusedImports (PagerState, OnboardingScreen.kt L7) TIDAK ter-flag di
        // run 206 padahal import itu mati → cetak config & classpath yang terpasang SAAT eksekusi (gradle-static-analysis-typed.log).
        doFirst {
            logger.lifecycle(
                "DETEKT_TYPED_DIAG config=" + config.files.joinToString(",") { it.name } +
                    " classpathEntries=" + classpath.files.size + " jvmTarget=" + jvmTarget
            )
        }
    }
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
    // Fitur baru: in-app update (UpdateManager.kt). Okio dipakai KHUSUS buat chunk
    // streaming unduhan APK Release (Feature Lock standing user: DILARANG readBytes(),
    // resiko OOM) — bukan buat networking (masih HttpURLConnection bawaan Android, 0
    // dependency HTTP client baru ditambahkan, biar tetap minim seperti gaya project ini).
    implementation("com.squareup.okio:okio:3.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}
