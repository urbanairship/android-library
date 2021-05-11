
import org.gradle.api.Project

internal fun Project.prop(name: String) = findProperty(name)?.toString()

internal fun env(name: String) = System.getenv(name)
