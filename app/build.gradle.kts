// kotlin
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.d4vram.pillmate2"
    compileSdk = 36

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    defaultConfig {
        applicationId = "com.d4vram.pillmate"
        manifestPlaceholders["appLabel"] = "@string/app_name"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "2.0.0-beta.3"

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
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    // Con -Pdev, la build debug se instala como app aparte ("PillMate Dev") para probar cambios
    // sin tocar la app de uso diario ni su base de datos:  ./gradlew :app:assembleDebug -Pdev
    // -Pdev=<id> usa otro applicationId: uno desechable para los tests de UI, que escriben en los
    // ajustes de la app y se desinstalan al acabar (ver docs: nunca lanzarlos contra la app diaria).
    if (project.hasProperty("dev")) {
        val devId = (project.property("dev") as String).ifBlank { "dev.d4vram.pillmate2" }
        androidComponents {
            onVariants(selector().withBuildType("debug")) {
                it.applicationId.set(devId)
                it.manifestPlaceholders.put("appLabel", "PillMate Dev")
            }
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

    // Declara a Android los idiomas de la app (los que tienen carpeta values-xx) para que
    // aparezca en Ajustes del sistema → Idiomas de aplicaciones. Ver res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        textReport = true
    }

    // Room exporta el esquema de cada versión a app/schemas/ (base para testear migraciones)
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// PrivacyContractTest revisa el manifest fusionado (el que acaba en el APK, con lo que añaden las librerías)
tasks.withType<Test>().configureEach {
    dependsOn("processDebugManifest")
    systemProperty(
        "mergedManifest",
        layout.buildDirectory.file("intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml").get().asFile.path
    )
}

dependencies {
    // Compose BOM — base para todas las libs de Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Activity
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI (versiones gestionadas por BOM)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Material 3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Foundation
    implementation("androidx.compose.foundation:foundation")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Room — base de datos local
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore — preferencias async
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Glance — widget con sintaxis Compose-like (NUEVO en v2.0-alpha)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // Utilidades
    implementation("com.github.skydoves:colorpicker-compose:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Imágenes
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Vico Charts
    implementation("com.patrykandpatrick.vico:compose:1.15.0")
    implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
