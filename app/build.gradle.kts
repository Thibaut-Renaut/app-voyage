import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
}

// Valeurs propres à la machine, lues dans local.properties (jamais commité) :
//   PWA_HOST=journalvoyage.netlify.app   → l'hôte de la PWA (App Link, bouton « Ouvrir la PWA »)
//   SYNC_BASE_URL=https://httpbin.org/   → la cible de l'outbox (le même endpoint que la PWA)
//   RELEASE_STORE_FILE / _PASSWORD / RELEASE_KEY_ALIAS / _PASSWORD → la clé release (facultatif)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String, default: String): String = localProps.getProperty(key) ?: default

val pwaHost: String = prop("PWA_HOST", "journalvoyage.netlify.app")
val syncBaseUrl: String = prop("SYNC_BASE_URL", "https://httpbin.org/")

android {
    namespace = "corsica.universita.journalvoyage"
    compileSdk = 37

    defaultConfig {
        applicationId = "corsica.universita.journalvoyage"
        minSdk = 26
        targetSdk = 37
        // +1 à chaque livraison : Android refuse d'installer un versionCode inférieur par-dessus.
        versionCode = 3
        versionName = "2.1"

        buildConfigField("String", "PWA_HOST", "\"$pwaHost\"")
        buildConfigField("String", "SYNC_BASE_URL", "\"$syncBaseUrl\"")
        manifestPlaceholders["pwaHost"] = pwaHost

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Keystore debug du cours : une seule empreinte SHA-256 à déclarer (assetlinks.json, Firebase).
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Bonus Jour 6 : la clé release vient de local.properties ; sans clé, la release est signée debug.
        create("release") {
            if (prop("RELEASE_STORE_FILE", "").isNotEmpty()) {
                storeFile = rootProject.file(prop("RELEASE_STORE_FILE", ""))
                storePassword = prop("RELEASE_STORE_PASSWORD", "")
                keyAlias = prop("RELEASE_KEY_ALIAS", "")
                keyPassword = prop("RELEASE_KEY_PASSWORD", "")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Le schéma de chaque version de la base est exporté ici (1.json, 2.json…) et commité :
// c'est la référence des migrations et du test MigrationTest.
room { schemaDirectory("$projectDir/schemas") }

dependencies {
    // Compose + Material 3
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Activity, Lifecycle, Navigation 3
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)

    // Core, splash
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)

    // Room, WorkManager, DataStore
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)

    // Réseau
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json)

    // Firebase : push (FCM) et mesure (Analytics), un seul projet Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // CameraX et localisation
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.compose)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
