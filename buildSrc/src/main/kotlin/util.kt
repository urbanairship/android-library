
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.provideDelegate

internal fun Project.prop(name: String) = findProperty(name)?.toString()

internal fun env(name: String) = System.getenv(name)
