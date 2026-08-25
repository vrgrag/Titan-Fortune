import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = file("keystore/key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.titanfortune.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.titanfortune.titanfortunegame"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
        resValue("string", "app_name", "Titan Fortune")
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

    flavorDimensions += "edition"
    productFlavors {
        create("v1Simple") {
            dimension = "edition"
            versionCode = 2
            versionName = "1.0.1"
            buildConfigField("String", "GAME_EDITION", "\"V1_SIMPLE\"")
        }
        create("v2Standard") {
            dimension = "edition"
            versionCode = 3
            versionName = "1.0.2"
            buildConfigField("String", "GAME_EDITION", "\"V2_STANDARD\"")
        }
        create("v3Complete") {
            dimension = "edition"
            versionCode = 4
            versionName = "1.0.3"
            buildConfigField("String", "GAME_EDITION", "\"V3_COMPLETE\"")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

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
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
}

android.applicationVariants.all {
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
