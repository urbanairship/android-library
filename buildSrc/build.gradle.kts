plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle:${libs.versions.androidGradlePlugin.get()}")
    implementation(kotlin("gradle-plugin", version = libs.versions.kotlin.get()))
    implementation("com.gradleup.nmcp:nmcp:${libs.versions.nmcpPlugin.get()}")
    implementation("com.gradleup.nmcp.aggregation:com.gradleup.nmcp.aggregation.gradle.plugin:${libs.versions.nmcpPlugin.get()}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
}

group = "build"
