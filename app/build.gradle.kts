import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.d4vram.pillmate2"
    compileSdk = 35

    // Cargar configuración del keystore
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    defaultConfig {
        applicationId = "com.d4vram.pillmate2"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }  
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug también usa el release keystore para evitar conflictos
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Establecemos un archivo de baseline. Lint ignorará los errores listados aquí.
        // baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
        textReport = true
    }
}

dependencies {
    // BOM de Compose (gestiona todas las versiones)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Core de Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Material 3 para Compose (NO para XML)
    implementation("androidx.compose.material3:material3")

    // Navigation para Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.biometric:biometric:1.1.0")

    // ==== ICONOS ====
    // Paquete que incluye la mayoría de iconos (FileDownload, FileUpload, Backup, QuestionMark, VolumeUp, etc.)
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel para Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // DataStore para preferencias
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Foundation para drag and drop
    implementation("androidx.compose.foundation:foundation:1.6.0")

    // AppCompat para el tema básico
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Room para persistencia avanzada con SQLite
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Rueda de selección de colores
    implementation("com.github.skydoves:colorpicker-compose:1.0.0")
    
    // Serialización Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // ZIP4J para archivos ZIP cifrados
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Accompanist para manejar permisos en Compose
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Coil para cargar y mostrar imágenes
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ExifInterface para corregir orientación de fotos
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}