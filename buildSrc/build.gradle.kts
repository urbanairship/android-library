plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle:${libs.versions.androidGradlePlugin.get()}")
    implementation(kotlin("gradle-plugin", version = libs.versions.kotlin.get()))
    implementation("com.gradleup.nmcp:nmcp:${libs.versions.nmcpPlugin.get()}")
    implementation("com.gradleup.nmcp.aggregation:com.gradleup.nmcp.aggregation.gradle.plugin:${libs.versions.nmcpPlugin.get()}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")

    // Workaround for BCV and AGP9 See: https://github.com/tjokinen/android-bcv-bridge
    implementation("io.github.tjokinen:android-bcv-bridge:${libs.versions.bcv.bridge.get()}")
    // The bridge bundles BCV at runtime only, so we still need to declare it here
    implementation("org.jetbrains.kotlinx:binary-compatibility-validator:${libs.versions.binary.compatibility.validator.get()}")
}

group = "build"
