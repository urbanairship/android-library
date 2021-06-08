import java.io.File
import java.net.URL
import java.time.Year
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTaskPartial

class AirshipDokkaPlugin : Plugin<Project> {

    private lateinit var dokkaVersion: String

    override fun apply(target: Project) = target.run {
        dokkaVersion = requireNotNull(rootProject.findProperty("dokkaVersion")?.toString()) {
            "Root project 'dokkaVersion' property not found! Verify that it is defined in buildscript.ext"
        }

        if (this == rootProject) {
            configureDokkaProject()
        } else {
            pluginManager.withPlugin("android-library") {
                configureDokkaModule()
            }
        }
    }

    private fun Project.configureDokkaProject() {
        apply<DokkaPlugin>()

        val dokkaHtmlMultiModule = tasks.named<DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
            dependencies.addDokkaAndroidPlugin()

            moduleVersion.set(rootProject.version.toString())
            moduleName.set("Airship Android SDK")

            outputDirectory.set(buildDir.resolve("docs/kdoc"))

            configureDokkaBasePlugin(rootDir)
        }

        tasks.register<Tar>("packageKDoc") {
            dependsOn(dokkaHtmlMultiModule)
            group = JavaBasePlugin.DOCUMENTATION_GROUP
            description = "Generates documentation in KDoc format"

            into("./") {
                from("build/docs/kdoc")
            }

            archiveFileName.set("${rootProject.property("airshipVersion")}-kdoc.tar.gz")
            destinationDirectory.set(file("build/docs"))
            archiveExtension.set("tar.gz")
            compression = Compression.GZIP
        }
    }

    private fun Project.configureDokkaModule() {
        if (name.startsWith("urbanairship-") &&
            !name.contains("stub") &&
            !name.contains("test") &&
            // urbanairship-adm has nothing to document and causes dokka to log warnings
            name != "urbanairship-adm") {

            apply<DokkaPlugin>()

            tasks.withType<DokkaTaskPartial>().configureEach {
                dependencies.addDokkaAndroidPlugin()

                moduleVersion.set(project.version.toString())
                moduleName.set(project.name)

                dokkaSourceSets.maybeCreate("main").apply {
                    sourceRoot("src/main/java")
                    jdkVersion.set(8)
                    platform.set(Platform.jvm)
                    includeNonPublic.set(false)
                    skipEmptyPackages.set(true)

                    noJdkLink.set(false)
                    noStdlibLink.set(false)
                    noAndroidSdkLink.set(false)

                    sourceLink {
                        localDirectory.set(File("src/main/java"))
                        remoteUrl.set(URL("https://github.com/urbanairship/android-library/blob/${project.name}/src/main/java"))
                        remoteLineSuffix.set("#L")
                    }

                    perPackageOption {
                        matchingRegex.set("""com\.ad4screen\..*""")
                        suppress.set(true)
                    }
                }

                configureDokkaBasePlugin(rootDir)
            }
        }
    }

    /** Sets up custom assets, styles, footer message, and enables separating inherited members. */
    private fun AbstractDokkaTask.configureDokkaBasePlugin(rootDir: File) {
        pluginsMapConfiguration.set(mutableMapOf("org.jetbrains.dokka.base.DokkaBase" to """
            {
                "customAssets": [
                    "${rootDir.resolve("docs/dokka/docs_logo.svg")}",
                    "${rootDir.resolve("docs/dokka/logo-icon.svg")}"
                ],
                "customStyleSheets": [
                    "${rootDir.resolve("docs/dokka/logo-styles.css") }"
                 ],
                "footerMessage": "© ${Year.now().value} Airship",
                "separateInheritedMembers": true
            }
        """.trimIndent()))
    }

    /** Adds the Android Dokka plugin (for @hide support). */
    private fun DependencyHandler.addDokkaAndroidPlugin() {
        add("dokkaPlugin", "org.jetbrains.dokka:android-documentation-plugin:$dokkaVersion")
    }
}
