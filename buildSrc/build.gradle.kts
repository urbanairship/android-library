plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:4.1.3")
    implementation(kotlin("gradle-plugin", version = embeddedKotlinVersion))
    implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
}

group = "build"

gradlePlugin {
    plugins {
        register("airshipPublish") {
            id = "airship-publish"
            implementationClass = "AirshipPublishPlugin"
        }
    }
}