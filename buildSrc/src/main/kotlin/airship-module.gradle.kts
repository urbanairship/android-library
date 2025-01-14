import java.net.URI
import java.time.Year
import kotlin.apply
import org.gradle.api.JavaVersion
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.dokka")
}

interface AirshipModuleExtension {
    /** Set to `false` to apply common config without publishing or documenting the module. */
    val published: Property<Boolean>
}

val ext: AirshipModuleExtension = project.extensions.create("airshipModule")
ext.published.convention(true)

afterEvaluate {
    the<AirshipModuleExtension>().published.get().let { published ->
        if (published) {
            plugins.apply(AirshipPublishPlugin::class)
        }
    }
}

version = getMavenVersion()
group = "com.urbanairship.android"

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
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        targetSdk = targetSdkVersion
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = listOf("-Xexplicit-api=strict", "-Xjvm-default=all-compatibility")
    }

    lint {
        targetSdk = targetSdkVersion
        checkOnly += setOf("Interoperability", "NewApi", "InlinedApi")
    }
}

dependencies {
    dokkaPlugin("org.jetbrains.dokka:android-documentation-plugin:${libs.requiredVersion("dokka")}")
}

dokka {
    dokkaSourceSets {
        main {
            sourceRoots.from("src/main/java")

            sourceLink {
                localDirectory.set(file("src/main/java"))
                remoteUrl("https://github.com/urbanairship/android-library/blob/${project.name}/src/main/java")
                remoteLineSuffix.set("#L")
            }
        }

        configureEach {
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

internal val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun VersionCatalog.requiredVersion(alias: String): String =
    findVersion(alias).get().requiredVersion
