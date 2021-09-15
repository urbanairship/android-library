
import com.android.build.gradle.BaseExtension
import java.io.ByteArrayOutputStream
import org.gradle.api.Project
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.the

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

internal fun Project.getDexMethodCount(): String = try {
    val android = the<BaseExtension>()

    val dx = "${android.sdkDirectory}/build-tools/${android.buildToolsRevision}/dx"
    val jar = "$buildDir/intermediates/bundles/release/classes.jar"
    val output = ByteArrayOutputStream()
    exec {
        commandLine("../tools/dex-method-count.sh", dx, jar)
        standardOutput = output
    }
    output.toString().trim()
} catch (e: Exception) {
    println(e)
    ""
}
