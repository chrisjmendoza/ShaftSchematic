plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.android.shaftschematic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.shaftschematic"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.1"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    // No composeOptions with Kotlin 2.x

}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xlambdas=class")
    }
}

dependencies {
    // --- Core / AppCompat (if you still use any View-based screens)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(libs.androidx.core.ktx)

    // --- Activity / Lifecycle
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.ktx)                   // for viewModels()
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1") // or newer
    implementation(libs.kotlinx.serialization.json)

// latest stable as of now

    // --- Compose BOM controls versions for all Compose artifacts below
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Material 3 (Compose)
    implementation(libs.androidx.compose.material3)

    // Optional: Material Icons (Compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // --- Material (View System) ONLY if you actually use Material Views
    implementation(libs.androidx.compose.foundation)
    implementation("com.google.android.material:material:1.13.0")

    // --- Debug / Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
