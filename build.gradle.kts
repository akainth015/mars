import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    java
    kotlin("jvm") version "1.5.0"
    id("com.dua3.javafxgradle7plugin") version "0.0.9"

}

repositories {
    mavenCentral()
}

application {
    mainClass.set("mars.MarsLaunch")
    version = "4.5"
}
dependencies {
    implementation("commons-cli:commons-cli:1.4")
    implementation(kotlin("stdlib-jdk8"))
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

javafx {
    version = "15.0.1"
    modules = listOf("javafx.controls", "javafx.fxml")
}
