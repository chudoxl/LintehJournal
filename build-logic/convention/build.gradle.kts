plugins {
    `kotlin-dsl`
}

group = "io.github.chudoxl.linteh.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)  // D-10
    }
}

dependencies {
    compileOnly(libs.androidGradlePlugin)
    compileOnly(libs.kotlinGradlePlugin)
    compileOnly(libs.composeGradlePlugin)
    compileOnly(libs.kotlinComposeCompilerGradlePlugin)
}

gradlePlugin {
    plugins {
        register("lintechKmp") {
            id = "lintech-kmp"
            implementationClass = "LintechKmpConventionPlugin"
        }
        register("lintechCompose") {
            id = "lintech-compose"
            implementationClass = "LintechComposeConventionPlugin"
        }
        register("lintechTest") {
            id = "lintech-test"
            implementationClass = "LintechTestConventionPlugin"
        }
    }
}
