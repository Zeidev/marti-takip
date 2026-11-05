import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android plugin: AGP 9 has built-in Kotlin support.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Optional release signing configuration.
 *
 * Debug builds are always signed with the local debug keystore, so the CI
 * workflow does not need any secret to publish a working APK. When a release
 * build is requested and the four `RELEASE_*` environment variables (or the
 * matching gradle properties) are present, the release variant is signed with
 * the operator's own keystore; otherwise it simply falls back to unsigned.
 */
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
    ?: (project.findProperty("RELEASE_KEYSTORE_PATH") as String?)
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
    ?: (project.findProperty("RELEASE_KEYSTORE_PASSWORD") as String?)
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
    ?: (project.findProperty("RELEASE_KEY_ALIAS") as String?)
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
    ?: (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
val hasReleaseSigning = releaseKeystorePath != null &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null &&
    file(releaseKeystorePath).exists()

android {
    namespace = "com.martitakip.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.martitakip.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("tr", "en")

        // osmdroid requires a stable, unique User-Agent that identifies the app.
        // OSM tile policy: https://operations.osmfoundation.org/policies/tiles/
        buildConfigField("String", "OSM_USER_AGENT", "\"com.martitakip.app/1.0.0 (Android; saha takip uygulamasi)\"")
        buildConfigField("String", "OSM_TILE_URL", "\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"")
    }

    buildTypes {
        debug {
            // No applicationIdSuffix: the CI publishes this variant as the
            // installable field build, so it must keep the canonical package
            // name (and therefore the OSM User-Agent below).
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            buildConfigField("String", "OSM_USER_AGENT", "\"com.martitakip.app/1.0.0-debug (Android; saha takip uygulamasi)\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "OSM_USER_AGENT", "\"com.martitakip.app/1.0.0 (Android; saha takip uygulamasi)\"")

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(releaseKeystorePath!!)
                    storePassword = releaseKeystorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            } else {
                // No production keystore configured: sign with the debug key.
                //
                // An unsigned APK cannot be installed by an operator at all
                // ("Missing META-INF/MANIFEST.MF"), which would make the
                // release variant useless for the only thing it is for. A
                // debug-signed build is installable, and the CI warns loudly
                // that this is not a store-ready artifact.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
    }

    androidResources {
        // osmdroid ships its own tile cache configuration; keep resource shrinking safe.
        noCompress += "db"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    // Branded cold start on every supported API level: the theme below is the
    // window the launcher paints before the first Compose frame exists.
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // NavigationSuiteScaffold: bottom bar on a phone, rail on a tablet or an
    // unfolded foldable, drawer on a desktop-sized window. All three come from
    // the window size class, so the shell does not hand-roll a NavigationBar.
    implementation(libs.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.osmdroid.android)

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
