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

    // Kotlin target is configured via kotlin { compilerOptions { ... } } below.

    buildFeatures {
        compose = true
    }
    // No composeOptions with Kotlin 2.x

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xlambdas=class")
    }
}

dependencies {
    // --- Core / AppCompat (if you still use any View-based screens)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(libs.androidx.core.ktx)

    // --- Activity / Lifecycle
    implementation(libs.androidx.activity.compose)           // 1.9.2
    implementation(libs.androidx.lifecycle.runtime.compose)  // 2.8.4
    implementation(libs.androidx.activity.ktx)               // for viewModels()
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    // Compose BOM controls versions of Compose artifacts below
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation("androidx.compose.ui:ui")
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.activity:activity-compose:1.9.2")

    // Material (View system) — only if you actually use Views; otherwise you can remove
    implementation("com.google.android.material:material:1.13.0")
    implementation(libs.androidx.navigation.compose)

    // --- Debug / Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// ─────────────────────────────────────────────────────────────────────────────
// Safety guard: prevent accidental on-device instrumentation test runs
//
// Rationale:
// Running connected instrumentation tests (e.g. `connectedAndroidTest`) on a
// personal device can uninstall/reinstall the app and wipe its internal storage.
// We make these tasks opt-in.
//
// Opt-in via one of:
//   - `-PallowConnectedAndroidTests=true`
//   - env var `ALLOW_CONNECTED_ANDROID_TESTS=1`
//
// This does NOT affect normal debug builds (`assembleDebug`, Android Studio Run).
// ─────────────────────────────────────────────────────────────────────────────
gradle.taskGraph.whenReady(
    org.gradle.api.Action<org.gradle.api.execution.TaskExecutionGraph> { graph ->
        val willRunConnectedTests = graph.allTasks.any { task: org.gradle.api.Task ->
            val name = task.name
        // Typical tasks: connectedAndroidTest, connectedDebugAndroidTest, connectedCheck
        name.equals("connectedAndroidTest", ignoreCase = true) ||
            name.equals("connectedCheck", ignoreCase = true) ||
            (name.contains("connected", ignoreCase = true) && name.contains("AndroidTest", ignoreCase = true))
        }

        if (willRunConnectedTests) {
            val allowProp = (project.findProperty("allowConnectedAndroidTests") as String?)
            val allow = allowProp.equals("true", ignoreCase = true) ||
                (System.getenv("ALLOW_CONNECTED_ANDROID_TESTS") == "1")

            if (!allow) {
                throw GradleException(
                    "Blocked connected-device instrumentation tests by default. " +
                        "These can uninstall/reinstall the app and wipe internal saves. " +
                        "To run anyway, pass -PallowConnectedAndroidTests=true " +
                        "or set ALLOW_CONNECTED_ANDROID_TESTS=1."
                )
            }
        }
    }
)
