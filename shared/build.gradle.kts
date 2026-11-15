import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The platform-free core: model, geometry, the document codec, formatting and the other pure code
// the drawing pipeline reads. `commonMain` may not touch `java.*` or `android.*`; the few platform
// calls it needs (UUIDs, fixed-point number formatting, the JDK's rounding and angle conversion,
// the clock) are `expect` declarations whose Android `actual`s are the original JVM expressions.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.android.shaftschematic.shared"
        compileSdk = 36
        minSdk = 28

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
