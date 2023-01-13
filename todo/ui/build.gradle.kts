import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id ("application")
    id ("org.openjfx.javafxplugin") version "0.0.13"
    id ("org.beryx.jlink") version "2.24.1"

    kotlin("jvm") version "1.7.10"
    kotlin("plugin.spring") version "1.7.10"
    kotlin("plugin.jpa") version "1.7.10"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

application {
    // Define the main class for the application.
    mainClassName = "todo.ui.MainKt"
}

javafx {
    version = "16"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.1.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.ktor:ktor-client-core:2.1.2")
    implementation("io.ktor:ktor-client-cio:2.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:2.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.2")
    implementation("org.controlsfx:controlsfx:11.1.1")
    implementation(project(":dtos"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
