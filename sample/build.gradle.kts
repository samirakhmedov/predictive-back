plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.samirakhmedov.predictiveback.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.samirakhmedov.predictiveback.sample"
        minSdk = 33
        targetSdk = 36
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":predictive-back"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}
