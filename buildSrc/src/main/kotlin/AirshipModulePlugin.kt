import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.provideDelegate

abstract class AirshipModuleExtension {
    /** Set to `false` to apply common config without publishing or documenting the module. */
    abstract val published: Property<Boolean>

    init {
        published.convention(true)
    }
}

class AirshipModulePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = target.run {
        val ext: AirshipModuleExtension = project.extensions.create("airshipModule")

        version = getMavenVersion()
        group = "com.github.freetradeUmair"

        applyCommonModulePlugins()
        configureAndroid()

        afterEvaluate {
            if (ext.published.get()) { applyPublishedModulePlugins() }
        }
    }

    /** Apply plugins common to all modules. */
    private fun Project.applyCommonModulePlugins() {
        apply<LibraryPlugin>()
    }

    /** Apply documentation and publishing plugins to public modules. */
    private fun Project.applyPublishedModulePlugins() {
        apply<AirshipDokkaPlugin>()
        apply<AirshipPublishPlugin>()
    }

    private fun Project.configureAndroid() {
        val compileSdkVersion: Int by rootProject
        val minSdkVersion: Int by rootProject
        val targetSdkVersion: Int by rootProject

        configure<LibraryExtension> {
            compileSdk = compileSdkVersion

            defaultConfig {
                minSdk = minSdkVersion
                targetSdk = targetSdkVersion

                buildConfigField("String", "AIRSHIP_VERSION", "\"${getMavenVersion()}\"")
                buildConfigField("String", "SDK_VERSION", "\"${getSdkVersionString()}\"")

                consumerProguardFiles("proguard-rules.pro")

                javaCompileOptions {
                    annotationProcessorOptions {
                        arguments += listOf(
                            "room.schemaLocation" to "$projectDir/schemas",
                            "room.incremental" to "true"
                        )
                    }
                }

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            sourceSets.getByName("androidTest") {
                assets.srcDir("$projectDir/schemas")
            }

            compileOptions.apply {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            lint {
                this.checkOnly += setOf("Interoperability", "NewApi", "InlinedApi")
            }

            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }
}
