import org.gradle.api.Plugin
import org.gradle.api.Project

class LintechKmpConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.library")
        }

        configureKotlinMultiplatform()
        configureAndroidLibrary()
    }
}
