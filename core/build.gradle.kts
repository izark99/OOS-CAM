plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runFixtures") {
    group = "verification"
    description = "Runs the planners on a fixtures directory and writes *_actual.json files."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.ooscam.core.RunFixturesKt")
    workingDir = rootProject.projectDir
}
