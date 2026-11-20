import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    apply(plugin = "com.diffplug.spotless")
    // .editorconfig values do not reach ktlint through Spotless here; this override mirrors .editorconfig, which stays for IDEs.
    val ktlintOverrides =
        mapOf(
            "ktlint_code_style" to "android_studio",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable"
        )
    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versions.ktlint.get())
                .editorConfigOverride(ktlintOverrides)
                .customRuleSets(
                    listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}")
                )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
                .editorConfigOverride(ktlintOverrides)
        }
    }
}
