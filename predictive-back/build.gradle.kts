plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.junit5)
    id("maven-publish")
}

group = "com.github.samirakhmedov"
version = "0.1.0"

android {
    namespace = "io.github.samirakhmedov.predictiveback"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    buildFeatures {
        compose = true
    }

    lint {
        lintConfig = rootProject.file("lint.xml")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.samirakhmedov"
            artifactId = "predictive-back"
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("predictive-back")
                description.set("Android 17 Settings-style predictive back for Navigation 3")
                url.set("https://github.com/samirakhmedov/predictive-back")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/samirakhmedov/predictive-back")
                }
            }
        }
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.navigation3.ui)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
