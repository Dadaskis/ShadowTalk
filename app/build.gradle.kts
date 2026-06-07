plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shadowtalk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shadowtalk"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        // Enable ViewBinding so we can access views safely without findViewById
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX core and appcompat for backward-compatible APIs
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Material Design components (MaterialButton, MaterialCheckBox, etc.)
    implementation("com.google.android.material:material:1.11.0")

    // ConstraintLayout for flexible UI layout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Activity Result API for picking audio files from storage
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Kotlin coroutines for background audio work on IO dispatcher
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle scope so coroutines cancel when the activity is destroyed
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
