// Root build file.
//
// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the
// `org.jetbrains.kotlin.android` plugin is no longer applied anywhere.
//
// AGP 9.4 has a *runtime* dependency on KGP 2.2.10 and KSP 2.2.10-2.0.2. To use
// the newer Kotlin 2.4.21 / KSP 2.3.13, the versions are pinned on the
// buildscript classpath here - this is the documented way to raise them.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    // The Compose compiler is a Kotlin compiler plugin; it must stay in sync
    // with the Kotlin version, so it is applied separately from AGP.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
