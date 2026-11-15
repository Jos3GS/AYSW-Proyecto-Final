import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.kover)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
        }

        androidMain.dependencies {
            implementation(libs.compose.ui.tooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.security.crypto)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(
                npm(
                    "@js-joda/timezone",
                    libs.versions.js.joda.timezone
                        .get(),
                ),
            )
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.koin.test)
            implementation(libs.compose.ui.test)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }
    }
}

android {
    namespace = "com.itm.servicedesk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.itm.servicedesk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

val isReleaseBuild = (findProperty("isRelease") as String?)?.toBoolean() ?: false
val releaseApiUrl = (findProperty("releaseApiUrl") as String?) ?: "https://api.dominio-proyecto.com"
val apiBaseUrlOverride = findProperty("apiBaseUrl") as String?
val webDevApiUrl = apiBaseUrlOverride ?: (findProperty("webDevApiUrl") as String?) ?: "http://localhost:8080"
val androidDevApiUrl = apiBaseUrlOverride ?: (findProperty("androidDevApiUrl") as String?) ?: "http://10.0.2.2:8080"

buildkonfig {
    packageName = "com.itm.servicedesk.config"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.BOOLEAN, "IS_RELEASE", isReleaseBuild.toString())
        buildConfigField(FieldSpec.Type.STRING, "RELEASE_API_URL", releaseApiUrl)
        buildConfigField(FieldSpec.Type.STRING, "WEB_DEV_API_URL", webDevApiUrl)
        buildConfigField(FieldSpec.Type.STRING, "ANDROID_DEV_API_URL", androidDevApiUrl)
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.itm.servicedesk.config.BuildKonfig",
                    "*.BuildConfig",
                    "*.R",
                    "*.R\$*",
                    "*ComposableSingletons*",
                )
            }
        }
        verify {
            rule("Minimum 90% coverage") {
                minBound(90)
            }
        }
    }
}

tasks.register("commonTest") {
    group = "verification"
    description = "Runs commonTest across Android unit tests and WasmJs browser tests"
    dependsOn("testDebugUnitTest", "wasmJsBrowserTest")
}
