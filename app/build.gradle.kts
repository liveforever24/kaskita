plugins {
    id("com.android.application")
}

android {
    namespace = "com.ylowstone.kaskita"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ylowstone.kaskita"
        minSdk = 29          // Android 10
        targetSdk = 35       // Android 17
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Menggunakan debug signingConfig agar langsung bisa diinstall keluarga dari GitHub Actions
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
