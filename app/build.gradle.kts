plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.myai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    // Permanent signing key: keeps every update installable over the last one
    // (so users stay logged in and never lose chats). Used only if the file exists.
    signingConfigs {
        getByName("debug") {
            val ks = file("aura.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "aura2026"
                keyAlias = "aura"
                keyPassword = "aura2026"
            }
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")  // read text from PDFs
    implementation("com.google.android.gms:play-services-ads:23.6.0")  // rewarded ads
}
