plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.gn4k.videocall"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gn4k.videocall"
        minSdk = 24
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

    buildFeatures {
        viewBinding  = true
        //noinspection DataBindingWithoutKapt
        dataBinding = true
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-messaging-directboot:24.1.1")

    // Dagger & Hilt
    implementation("com.google.dagger:dagger:2.27")
    kapt("com.google.dagger:dagger-compiler:2.27")
    implementation("com.google.dagger:hilt-android:2.44")
    kapt("com.google.dagger:hilt-android-compiler:2.44")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    // Other UI tools
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.19")
    implementation("jp.wasabeef:picasso-transformations:2.4.0")
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation("com.vanniktech:android-image-cropper:4.5.0")

    // Google Play Services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Mesibo WebRTC
    implementation("com.mesibo.api:webrtc:1.0.5")

    // Utilities
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.multidex:multidex:2.0.1")
}


kapt {
    correctErrorTypes = true
}