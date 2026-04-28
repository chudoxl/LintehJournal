import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(17)  // D-10

        // D-06: android + iosX64 + iosArm64 + iosSimulatorArm64
        // applyDefaultHierarchyTemplate включён через kotlin.mpp.applyDefaultHierarchyTemplate=true
        // в gradle.properties; targets configure-ются в module-level build.gradle.kts.

        // freeCompilerArgs — strict expect/actual matching (Kotlin 2.0+)
        targets.configureEach {
            compilations.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.add("-Xexpect-actual-classes")
                    }
                }
            }
        }

        sourceSets.named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
