plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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

    sourceSets {
        // MigrationTestHelper reads exported Room schemas from assets. Under Robolectric that is the
        // merged debug assets (unit-test assets are not merged), so debug builds carry them; release does not.
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // AcceptanceUseCaseTest reads the base fixtures and writes X01_plan.json for run_acceptance.py.
            // Declared as task input and output so a cached or up-to-date run still provides the file.
            val fixtures = rootProject.layout.projectDirectory.dir("testcase/fixtures/base")
            val acceptanceOut = layout.buildDirectory.dir(if (test.name == "testDebugUnitTest") "acceptance" else "acceptance-${test.name}")
            test.inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("acceptanceFixtures")
            test.outputs.dir(acceptanceOut).withPropertyName("acceptanceOut")
            test.systemProperty("ooscam.fixturesDir", fixtures.asFile.absolutePath)
            test.systemProperty("ooscam.acceptanceOut", acceptanceOut.get().asFile.absolutePath)
            test.testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = false
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Export (Phase 3); the Phase 0 spike uses it too.
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.transformer)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.serialization.json)
}
