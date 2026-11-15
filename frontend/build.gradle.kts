plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.buildkonfig) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover) apply false
}

ktlint {
    val ktlintCliVersion =
        libs.versions.ktlint.cli
            .get()
    version.set(ktlintCliVersion)
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { element ->
            val path = element.file.path
            path.contains("/build/") || path.contains("\\build\\")
        }
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        val ktlintCliVersion =
            rootProject.libs.versions.ktlint.cli
                .get()
        version.set(ktlintCliVersion)
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude { element ->
                val path = element.file.path
                path.contains("/build/") || path.contains("\\build\\")
            }
        }
    }
}
