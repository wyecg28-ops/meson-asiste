import java.util.Properties

// Firma oficial: vive fuera del proyecto (Documentos/MesonAsiste-Firma) y nunca se sube a GitHub.
// Las actualizaciones solo se instalan si vienen firmadas con la misma firma.
val firma = Properties().apply {
    val archivo = file(System.getProperty("user.home") + "/Documents/MesonAsiste-Firma/firma.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.asistencia"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.asistencia"
        minSdk = 23
        targetSdk = 37
        // Subir los dos en cada versión publicada (la app avisa si hay una mayor).
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("oficial") {
            if (!firma.isEmpty) {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // También la versión de Android Studio usa la firma oficial: así no choca con las actualizaciones.
        debug {
            if (!firma.isEmpty) signingConfig = signingConfigs.getByName("oficial")
        }
        release {
            if (!firma.isEmpty) signingConfig = signingConfigs.getByName("oficial")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    // Lector de QR de Google (sin permiso de cámara) y generador del QR de la entrada.
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}