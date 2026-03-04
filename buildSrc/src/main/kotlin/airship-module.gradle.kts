import java.time.Year
import org.gradle.api.JavaVersion
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    id("com.android.library")
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("com.gradleup.nmcp")
}

version = getMavenVersion()
group = "com.urbanairship.android"

//
// Kotlin
//

kotlin {
    explicitApi = ExplicitApiMode.Strict

    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        jvmDefault = JvmDefaultMode.ENABLE
    }
}

//
// Android
//

android {
    val compileSdkVersion: Int by rootProject
    val minSdkVersion: Int by rootProject
    val targetSdkVersion: Int by rootProject

    compileSdk = compileSdkVersion
    defaultConfig {
        minSdk = minSdkVersion

        buildConfigField("String", "AIRSHIP_VERSION", "\"${getMavenVersion()}\"")
        buildConfigField("String", "SDK_VERSION", "\"${getSdkVersionString()}\"")

        consumerProguardFiles("proguard-rules.pro")

        buildFeatures {
            buildConfig = true
            resValues = true
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        targetSdk = targetSdkVersion
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        targetSdk = targetSdkVersion
        checkOnly += setOf("Interoperability", "NewApi", "InlinedApi")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

//
// Signing
//

val secretKey = env("SIGNING_KEY") ?: prop("signing.key")
val keyId = env("SIGNING_KEY_ID") ?: prop("signing.keyId")
val password = env("SIGNING_PASSWORD") ?: prop("signing.password")

if (!secretKey.isNullOrEmpty()) {
    apply<SigningPlugin>()

    val publishing = the<PublishingExtension>()

    configure<SigningExtension> {
        isRequired = true
        sign(publishing.publications)
        if (keyId != null) {
            useInMemoryPgpKeys(keyId, secretKey, password)
        } else {
            useInMemoryPgpKeys(secretKey, password)
        }
    }
}

//
// Publishing
//

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("Production") {
                from(components["release"])

                pom {
                    name.set(project.name)
                    description.set(project.description)
                    url.set("https://github.com/urbanairship/android-library")

                    developers {
                        developer { name.set("Airship") }
                    }

                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }

                    scm {
                        connection.set("https://github.com/urbanairship/android-library.git")
                        url.set("https://github.com/urbanairship/android-library")
                    }
                }
            }
        }
    }
}

//
// Dokka
//

dependencies {
    dokkaPlugin("org.jetbrains.dokka:android-documentation-plugin:${libs.requiredVersion("dokka")}")
}

dokka {
    dokkaSourceSets {
        configureEach {

            if (name == "release") {
                sourceRoots.from("src/main/java")

                sourceLink {
                    localDirectory.set(file("src/main/java"))
                    remoteUrl("https://github.com/urbanairship/android-library/blob/main/${project.name}/src/main/java")
                    remoteLineSuffix.set("#L")
                }
            }

            suppressGeneratedFiles.set(true)
            skipEmptyPackages.set(true)

            documentedVisibilities(
                VisibilityModifier.Public, VisibilityModifier.Protected
            )

            jdkVersion.set(8)

            enableKotlinStdLibDocumentationLink.set(true)
            enableJdkDocumentationLink.set(true)
            enableAndroidDocumentationLink.set(true)
        }
    }

    pluginsConfiguration.html {
        customAssets.from("$rootDir/docs/dokka/logo-icon.svg")
        footerMessage.set("©${Year.now().value} Airship")
        separateInheritedMembers.set(true)
    }
}

//
// Helpers
//

internal fun Project.getSdkVersionString(): String {
    val airshipVersionQualifier: String? by project
    val airshipVersion: String by project

    val parts = mutableListOf("!SDK-VERSION-STRING!", group, name)
    airshipVersionQualifier?.let(parts::add)
    parts.add(airshipVersion)

    return parts.joinToString(":")
}

internal fun Project.getMavenVersion(): String {
    val airshipVersion: String by rootProject
    val airshipVersionQualifier: String? by rootProject
    val versionSuffix: String? by rootProject

    val parts = mutableListOf(airshipVersion)
    airshipVersionQualifier?.let(parts::add)
    versionSuffix?.let(parts::add)

    return parts.joinToString("-")
}

internal fun Project.prop(name: String) = findProperty(name)?.toString()

internal fun env(name: String) = System.getenv(name)

internal val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun VersionCatalog.requiredVersion(alias: String): String =
    findVersion(alias).get().requiredVersion
