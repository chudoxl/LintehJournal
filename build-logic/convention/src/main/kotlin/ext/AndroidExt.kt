import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal fun Project.configureAndroidLibrary() {
    extensions.configure<LibraryExtension> {
        compileSdk = 35  // D-11
        defaultConfig {
            minSdk = 26  // D-11
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17  // D-10
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
