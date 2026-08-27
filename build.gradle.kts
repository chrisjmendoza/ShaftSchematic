// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // On the buildscript classpath but never applied here: `:app` applies them itself, and only
    // when a google-services.json is present. Declaring them `apply false` is what makes that
    // conditional `apply(plugin = …)` resolvable.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}