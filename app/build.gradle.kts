plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.aegis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aegis"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    // TFLite model files must not be compressed
    aaptOptions {
        noCompress("tflite")
    }
}

// Room schema export location (Phase 2)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


dependencies {
    // Compose BOM — versions for all compose-* artifacts come from here
    implementation(platform(libs.androidx.compose.bom))

    // ── Core Android ─────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose UI ───────────────────────────────────────────────────────────
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // ── Navigation (Phase 1) ─────────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Lifecycle / ViewModel ────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // ── Hilt DI (Phase 1) ────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Room + SQLCipher (Phase 2) ────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    // ── Biometric (Phase 3) ───────────────────────────────────────────────────
    implementation(libs.biometric)

    // ── ML Kit OCR (Phase 6) ──────────────────────────────────────────────────
    implementation(libs.mlkit.text.recognition)

    // ── TFLite (Phase 7) ──────────────────────────────────────────────────────
    implementation(libs.tflite)
    implementation(libs.tflite.support)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── QR Code generation (Phase 9) ─────────────────────────────────────────
    implementation(libs.zxing.core)

    // ── Image loading (vault thumbnails) ─────────────────────────────────────
    implementation(libs.coil.compose)

    // ── DataStore preferences ─────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Security Crypto (Phase 3 key management) ──────────────────────────────
    implementation(libs.security.crypto)

    // ── Gemini API (Phase 11) ─────────────────────────────────────────────────
    implementation(libs.generativeai)

    // ── LiteRT-LM (Gemma inference — replaces tasks-genai) ───────────────────
    implementation(libs.litertlm)

    // ── PDFBox Android (PDF text extraction) ─────────────────────────────────
    implementation(libs.pdfbox.android)

    // ── OkHttp (ASTP relay HTTP + SSE) ───────────────────────────────────────
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.sse)

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
