plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.slip.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.slip.app"
        minSdk = 26
        targetSdk = 36
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
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity)
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // WorkManager for background operations
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // mDNS discovery
    implementation("javax.jmdns:jmdns:3.5.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}