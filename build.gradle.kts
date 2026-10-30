// Root build file. Plugins are declared here (without applying them) so that
// every module resolves exactly the same plugin versions from the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
