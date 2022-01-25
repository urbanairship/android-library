
import com.android.build.gradle.LibraryExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the

class AirshipDoclavaPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.run {
        if (this == rootProject) {
            val doclava = configurations.create("doclava")
            val doclavaVersion: String by rootProject
            dependencies {
                "doclava"("com.google.doclava:doclava:$doclavaVersion")
            }
            configureDoclava(doclava)
        }
    }

    private fun Project.configureDoclava(doclava: Configuration) {
        val javadoc = tasks.register<Javadoc>("javadoc") {
            group = JavaBasePlugin.DOCUMENTATION_GROUP
            description = "Generates documentation in Javadoc format"

            setDestinationDir(buildDir.resolve("docs/javadoc"))
            title = null
            isFailOnError = false

            exclude("**/BuildConfig.java")
            exclude("**/R.java")
            exclude("**/com/ad4screen/**")

            options {
                this as StandardJavadocDocletOptions

                docletpath = doclava.files.toList()
                doclet("com.google.doclava.Doclava")
                bootClasspath = listOf(File(System.getenv("JAVA_HOME") + "/jre/lib/rt.jar"))
                addStringOption("notimestamp", null)
                addStringOption("hdf project.name", "Airship Android SDK")
                addStringOption("hdf project.version", project.version.toString())
                addStringOption("apiversion", "v2")
                addStringOption("federate android", "http://developer.android.com/reference")
                addStringOption("federationxml android", "docs/android-22.xml")
                addStringOption("templatedir", "docs/template")
            }
        }

        subprojects.forEach { proj ->
            proj.afterEvaluate {
                javadoc.configure {
                    if (proj.name in listOf(
                            "urbanairship-core",
                            "urbanairship-accengage",
                            "urbanairship-ads-identifier",
                            "urbanairship-automation",
                            "urbanairship-location",
                            "urbanairship-message-center",
                            "urbanairship-preference",
                            "urbanairship-chat")
                    ) {
                        val android = proj.the<LibraryExtension>()
                        val task: Javadoc = this
                        task.source += android.sourceSets["main"].java.getSourceFiles()
                        val androidBootClasspath = android.bootClasspath.joinToString(File.pathSeparator)
                        task.classpath += proj.files(androidBootClasspath)

                        android.libraryVariants.all {
                            val variant = this
                            if (variant.name == "release") {
                                task.classpath += variant.javaCompileProvider.get().classpath
                                task.classpath += project.files(androidBootClasspath)
                            }
                        }
                    }
                }
            }
        }

        tasks.register<Tar>("packageJavadoc") {
            dependsOn(javadoc)
            group = JavaBasePlugin.DOCUMENTATION_GROUP
            description = "Generate Javadoc archive"

            into("./") {
                from("build/docs/javadoc")
            }

            archiveFileName.set("${rootProject.property("airshipVersion")}.tar.gz")
            destinationDirectory.set(File("build/docs"))
            archiveExtension.set("tar.gz")
            compression = Compression.GZIP
        }
    }
}
