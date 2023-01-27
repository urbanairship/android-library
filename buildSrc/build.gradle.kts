plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.build:gradle:7.2.1")
    implementation(kotlin("gradle-plugin", version = "1.5.31"))
    implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.4.32")
}

group = "build"

gradlePlugin {
    plugins {
        register("airshipPublish") {
            id = "airship-publish"
            implementationClass = "AirshipPublishPlugin"
        }
        register("airshipDokka") {
            id = "airship-dokka"
            implementationClass = "AirshipDokkaPlugin"
        }
        register("airshipDoclava") {
            id = "airship-doclava"
            implementationClass = "AirshipDoclavaPlugin"
        }
        register("airshipModule") {
            id = "airship-module"
            implementationClass = "AirshipModulePlugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
