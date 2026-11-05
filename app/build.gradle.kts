plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase is optional: the app works fully offline. When the group adds its own
// app/google-services.json (from the Firebase console), cloud login, sync, and push
// notifications are switched on automatically.
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "ph.edu.asiatech.goananas"
    compileSdk = 35

    defaultConfig {
        applicationId = "ph.edu.asiatech.goananas"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("boolean", "FIREBASE_ENABLED", hasFirebase.toString())
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // the model file must stay uncompressed so TensorFlow Lite can memory-map it
    androidResources { noCompress += "tflite" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    // Android core and Jetpack Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Camera (CameraX) and image loading
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Image processing (same OpenCV steps as the paper) and the CNN model
    implementation("org.opencv:opencv:4.10.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // Local database, settings, background work and notifications
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Firebase (login, cloud database, photo storage, push notifications)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
