import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.testing.jacoco.plugins.JacocoPlugin

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
        group = "com.urbanairship.android"

        applyCommonModulePlugins()
        configureAndroid()
        registerTasks()

        afterEvaluate {
            if (ext.published.get()) { applyPublishedModulePlugins() }
        }
    }

    /** Apply plugins common to all modules. */
    private fun Project.applyCommonModulePlugins() {
        apply<LibraryPlugin>()
        // For test coverage reports
        apply<JacocoPlugin>()
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
            compileSdkVersion(compileSdkVersion)

            defaultConfig {
                minSdkVersion(minSdkVersion)
                targetSdkVersion(targetSdkVersion)

                buildConfigField("String", "AIRSHIP_VERSION", "\"${getMavenVersion()}\"")
                buildConfigField("String", "SDK_VERSION", "\"${getSdkVersionString()}\"")

                consumerProguardFiles("proguard-rules.pro")
            }

            compileOptions.apply {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility(JavaVersion.VERSION_1_8)
            }

            lintOptions.checkOnly("Interoperability")

            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }

    private fun Project.registerTasks() {
        val runDexMethodCount = tasks.register("runDexMethodCount") {
            doLast {
                val dexCount = "Dex Method Count: ${getDexMethodCount()}"

                val dex = buildDir.resolve("/dex-counts/release")
                dex.parentFile.mkdirs()
                dex.writeText(dexCount)
                println(dexCount)
            }
        }

        afterEvaluate {
            runDexMethodCount.dependsOn("assembleRelease")
        }
    }
}
