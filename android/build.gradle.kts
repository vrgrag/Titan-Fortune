import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = file("keystore/key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.titanfortune.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.titanfortune.titanfortunegame"
        minSdk = 30
        targetSdk = 35
        versionCode = 22
        versionName = "1.2.2"
        resValue("string", "app_name", "Titan Fortune")
        buildConfigField("String", "GAME_EDITION", "\"V3_COMPLETE\"")
        buildConfigField("String", "INLET_STATUS", "\"\"")
        buildConfigField("String", "INLET_PARAMS", "\"\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
            res.srcDirs("res")
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            kotlin.srcDirs("src")
        }
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    lint { abortOnError = false }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
            val inlet = Properties()
            val inletFile = file("inlet.properties")
            if (inletFile.exists()) inletFile.inputStream().use { inlet.load(it) }
            fun esc(raw: String): String =
                raw.replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "INLET_STATUS", "\"${esc(inlet.getProperty("inlet.forceStatus", ""))}\"")
            buildConfigField("String", "INLET_PARAMS", "\"${esc(inlet.getProperty("inlet.forceParams", ""))}\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro")
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.appsflyer:af-android-sdk:6.16.2")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")
}

android.applicationVariants.configureEach {
    if (buildType.name == "release") {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "TitanFortune-release.apk"
        }
    }
}

val localProps = Properties()
val lp = rootProject.file("local.properties")
if (lp.exists()) localProps.load(lp.inputStream())
