import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class LintechTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Mokkery — KMP-friendly mocking (D-03; replaces MockK on iOS targets)
        pluginManager.apply("dev.mokkery")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonTest") {
                dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.findLibrary("kotest-assertions").get())
                    implementation(libs.findLibrary("turbine").get())
                }
            }
        }
    }
}
