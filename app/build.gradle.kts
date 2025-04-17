plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.proximityalarmapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.proximityalarmapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures{
        viewBinding=true
        dataBinding = true
    }
}

dependencies {
    // Custom dependencies
    implementation(libs.androidx.drawerlayout)
    implementation(libs.google.material)
    implementation(libs.androidx.cardview) // для карточек CardView в XML

    // Basic dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // JitPack
    implementation(libs.github.vtm)

    //MapsForge
        // Maps
    implementation(libs.mapsforge.mapsforge.core)
    implementation(libs.mapsforge.map)
    implementation(libs.mapsforge.map.reader)
    implementation(libs.mapsforge.themes)
    // Android
    implementation(libs.mapsforge.map.android)
    implementation(libs.androidsvg)

    //POI
    implementation(libs.mapsforge.poi)
    // Kotlin Coroutines
    implementation (libs.kotlinx.coroutines.core)
    implementation (libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Android Lifecycle (для lifecycleScope)
    implementation (libs.androidx.lifecycle.runtime.ktx)

    // Fused Location API
    implementation(libs.play.services.location)
    // Асинхронные процессы
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.multiprocess)

}