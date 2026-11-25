plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.ooscam"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ooscam"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    // Spike only (Phase 0): debug builds, thrown away after the spike.
    debugImplementation(libs.androidx.lifecycle.service)
    debugImplementation(libs.androidx.camera.core)
    debugImplementation(libs.androidx.camera.camera2)
    debugImplementation(libs.androidx.camera.lifecycle)
    debugImplementation(libs.androidx.camera.video)
    debugImplementation(libs.androidx.media3.common)
    debugImplementation(libs.androidx.media3.effect)
    debugImplementation(libs.androidx.media3.transformer)
}

dependencies {
    testImplementation(libs.junit4)
}
