plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":db"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.exposed.java.time)
    testImplementation(libs.hikari)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.postgresql)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "com.chicot.turdalert.backend.ApplicationKt")
    }
}
