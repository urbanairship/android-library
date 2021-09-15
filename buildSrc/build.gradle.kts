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
