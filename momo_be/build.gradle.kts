plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    kotlin("plugin.serialization") version "2.3.0"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.config.yaml)
    implementation("io.ktor:ktor-server-websockets:3.4.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")

    // Ktor Client
    implementation("io.ktor:ktor-client-core:3.4.2")
    implementation("io.ktor:ktor-client-cio:3.4.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // dotenv
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // Database
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.45.0")

    // BCrypt
    implementation("org.mindrot:jbcrypt:0.4")

    // Firebase
    implementation("com.google.firebase:firebase-admin:9.2.0")

    // PDF & Crypto
    implementation("com.itextpdf:itext7-core:7.2.5")
    implementation("com.itextpdf:sign:7.2.5")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // Redis
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")

    // YAML parser
    implementation("com.charleskorn.kaml:kaml:0.55.0")

    implementation(libs.logback.classic)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
