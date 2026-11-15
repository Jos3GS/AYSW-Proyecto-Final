plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    application
}

group = "com.itm.servicedesk"
version = "0.1.0-alpha"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.itm.servicedesk.ApplicationKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql.driver)

    implementation(libs.bcrypt)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

ktlint {
    version.set(libs.versions.ktlint.cli)
}

kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(tasks.named("koverVerify"))
}
