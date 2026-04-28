// Root build script — модули используют convention plugins из build-logic/.
// Plugins resolved here с `apply false` доступны во всех модулях через id(...) и в convention plugins
// через pluginManager.apply("...").
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinComposeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.mokkery) apply false
}
