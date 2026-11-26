import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
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
            val tokensJson = rootProject.layout.projectDirectory.file("design/design-system/oos-cam/tokens.json")
            test.inputs.file(tokensJson).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("tokensJson")
            test.systemProperty("ooscam.tokensJson", tokensJson.asFile.absolutePath)
            test.systemProperty("ooscam.acceptanceOut", acceptanceOut.get().asFile.absolutePath)
            test.testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = false
            }
        }
    }
}

/**
 * Generates ui/theme/Tokens.kt from the design system's tokens.json (the only source of colors, type,
 * spacing, radii and motion). The output is committed so scripts/check_tokens.sh can read it; preBuild
 * regenerates it and TokensTest fails when it is stale.
 */
abstract class GenerateTokensTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tokensJson: RegularFileProperty

    @get:OutputFile
    abstract val tokensKt: RegularFileProperty

    @TaskAction
    fun generate() {
        val src = tokensJson.get().asFile
        @Suppress("UNCHECKED_CAST")
        val tokens = (groovy.json.JsonSlurper().parse(src) as Map<String, Any>)["tokens"] as Map<String, String>
        val sha = MessageDigest.getInstance("SHA-256").digest(src.readBytes())
            .joinToString("") { "%02x".format(it) }
        fun camel(n: String) = Regex("-(.)").replace(n) { it.groupValues[1].uppercase() }
        fun color(css: String): String {
            Regex("#([0-9A-Fa-f]{6})").matchEntire(css)?.let { return "Color(0xFF${it.groupValues[1].uppercase()})" }
            val p = Regex("rgba\\(([^)]*)\\)").matchEntire(css)?.groupValues?.get(1)?.split(",")?.map { it.trim() }
                ?: error("unsupported color $css")
            val argb = listOf(Math.round(p[3].toDouble() * 255).toInt(), p[0].toInt(), p[1].toInt(), p[2].toInt())
            return "Color(0x" + argb.joinToString("") { "%02X".format(it) } + ")"
        }
        val lines = mutableListOf<String>()
        for ((name, css) in tokens) {
            if (name.startsWith("demo-")) continue
            val id = camel(name)
            val value = when {
                name.startsWith("color-") -> color(css)
                name == "font-family" -> "FontFamily(Font(R.font.inter_regular, FontWeight.W400), Font(R.font.inter_medium, FontWeight.W500), Font(R.font.inter_semibold, FontWeight.W600))"
                name.startsWith("font-") -> {
                    val m = Regex("(\\d+) (\\d+)px/(\\d+)px").find(css)?.groupValues ?: error("unsupported font $css")
                    "TextStyle(fontFamily = fontFamily, fontWeight = FontWeight(${m[1]}), fontSize = ${m[2]}.sp, lineHeight = ${m[3]}.sp)"
                }
                css.matches(Regex("\\d+px")) -> css.removeSuffix("px") + ".dp"
                css.matches(Regex("\\d+ms")) -> css.removeSuffix("ms")
                css.startsWith("cubic-bezier(") -> {
                    val p = css.removePrefix("cubic-bezier(").removeSuffix(")").split(",").map { it.trim() + "f" }
                    "CubicBezierEasing(${p.joinToString(", ")})"
                }
                else -> error("unsupported token $name: $css")
            }
            lines += "    /** `$name`: $css */\n    val $id = $value\n"
        }
        val ids = tokens.keys.filterNot { it.startsWith("demo-") }.map(::camel)
        val out = buildString {
            append("// GENERATED by :app:generateTokens from design/design-system/oos-cam/tokens.json. Do not edit.\n")
            append("package io.github.ooscam.ui.theme\n\n")
            append("import androidx.compose.animation.core.CubicBezierEasing\n")
            append("import androidx.compose.ui.graphics.Color\n")
            append("import androidx.compose.ui.text.TextStyle\n")
            append("import androidx.compose.ui.text.font.Font\n")
            append("import androidx.compose.ui.text.font.FontFamily\n")
            append("import androidx.compose.ui.text.font.FontWeight\n")
            append("import androidx.compose.ui.unit.dp\n")
            append("import androidx.compose.ui.unit.sp\n")
            append("import io.github.ooscam.R\n\n")
            append("object Tokens {\n")
            append("    const val SOURCE_SHA256 = \"$sha\"\n\n")
            lines.forEach { append(it).append("\n") }
            append("    /** Every token by its Kotlin name (for tests and tooling). */\n")
            append("    val all: Map<String, Any> = mapOf(\n")
            ids.forEach { append("        \"$it\" to $it,\n") }
            append("    )\n}\n")
        }
        tokensKt.get().asFile.apply { parentFile.mkdirs() }.writeText(out)
    }
}

val generateTokens = tasks.register<GenerateTokensTask>("generateTokens") {
    tokensJson.set(rootProject.layout.projectDirectory.file("design/design-system/oos-cam/tokens.json"))
    tokensKt.set(layout.projectDirectory.file("src/main/java/io/github/ooscam/ui/theme/Tokens.kt"))
}
tasks.named("preBuild") { dependsOn(generateTokens) }

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    // Baselines are committed next to the tests; scripts/check_screens.py reads them from here.
    outputDir.set(file("src/test/screenshots"))
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
    implementation(libs.androidx.datastore.preferences)
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
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
