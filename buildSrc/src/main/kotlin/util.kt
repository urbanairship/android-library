
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.provideDelegate

internal fun Project.prop(name: String) = findProperty(name)?.toString()

internal fun env(name: String) = System.getenv(name)

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
