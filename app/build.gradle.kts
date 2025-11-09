/*
 * Shivansh: This is your file.
 * This tells Android Studio to download ARCore.
 * Make sure to click "Sync Now" after pasting this.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourcompany.navigoapp" // Make sure this matches your package name
    compileSdk = 34 // Use a recent SDK

    defaultConfig {
        applicationId = "com.yourcompany.navigoapp"
        minSdk = 24  // ARCore REQUIRES API 24 (Android 7.0) or higher
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Standard Android Dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // *** THIS IS THE MOST IMPORTANT LINE ***
    // ARCore (Google Play Services for AR)
    implementation("com.google.ar:core:1.41.0")

    // ARCore Sceneform UX
    // This provides the 'ArFragment' which makes setup much easier
    implementation("com.google.ar.sceneform.ux:sceneform-ux:1.17.1")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
