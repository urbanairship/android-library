import com.android.build.gradle.LibraryExtension
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import io.github.gradlenexus.publishplugin.NexusPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

class AirshipPublishPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = target.run {
        if (this == rootProject) {
            // Set up NexusPublishPlugin for the root project
             configureNexusPublish()
        } else {
            // Set up MavenPublishPlugin and SigningPlugin for module sub-projects
            pluginManager.withPlugin("android-library") {
//                configureMavenPublish()
                configureSigning()
            }
        }
    }

    private fun Project.configureNexusPublish() {
        val user = env("SONATYPE_USERNAME") ?: prop("sonatype.username")
        val pass = env("SONATYPE_PASSWORD") ?: prop("sonatype.password")
        // stagingProfileId is optional, but supposedly saves some time during publishing if set.
        val profileId = env("SONATYPE_STAGING_PROFILE_ID") ?: prop("sonatype.stagingProfileId")

        if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
            logger.debug("Missing publishing credentials! Nexus publishing will not be configured...")
            return
        }

        apply<NexusPublishPlugin>()

        configure<NexusPublishExtension> {
            repositories {
                sonatype {
                    username.set(user)
                    password.set(pass)
                    if (!profileId.isNullOrEmpty()) {
                        stagingProfileId.set(profileId)
                    }
                }
            }
        }
    }

    private fun Project.configureMavenPublish() {
        val android = the(LibraryExtension::class)

        val sourcesJar = tasks.register<Jar>("sourcesJar") {
            archiveClassifier.set("sources")
            from(android.sourceSets["main"].java.srcDirs)
        }

        apply<MavenPublishPlugin>()

        configure<PublishingExtension> {
            publications {
                register<MavenPublication>("Production") {
                    groupId = "com.github.freetradeUmair"
                    artifactId = project.name
                    version = project.version.toString()

//                    artifact("$buildDir/outputs/aar/${project.name}-release.aar")
//                    artifact(sourcesJar.get())

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

                        withXml {
                            val dependencies = asNode().appendNode("dependencies")
                            configurations["implementation"].allDependencies.forEach {
                                val dep = dependencies.appendNode("dependency")
                                dep.appendNode("groupId", it.group)
                                dep.appendNode("artifactId", it.name)
                                dep.appendNode("version", it.version)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Project.configureSigning() {
        val secretKey = env("SIGNING_KEY") ?: prop("signing.key")
        val secretKeyRingFile = env("SIGNING_SECRET_KEY_RING_FILE") ?: prop("signing.secretKeyRingFile")
        val keyId = env("SIGNING_KEY_ID") ?: prop("signing.keyId")
        val password = env("SIGNING_PASSWORD") ?: prop("signing.password")

        if (secretKey.isNullOrEmpty() && secretKeyRingFile.isNullOrEmpty()) {
            logger.debug("Missing signing key or secretKeyRingFile! Signing will not be configured...")
            return
        }

        apply<SigningPlugin>()

        pluginManager.withPlugin("maven-publish") {
            val publishing = the<PublishingExtension>()

            configure<SigningExtension> {
                isRequired = true
                sign(publishing.publications)
                if (secretKey != null) {
                    if (keyId != null) {
                        useInMemoryPgpKeys(keyId, secretKey, password)
                    } else {
                        useInMemoryPgpKeys(secretKey, password)
                    }
                }
            }
        }

        tasks.named("signProductionPublication") {
            dependsOn("bundleReleaseAar")
        }
    }
}
