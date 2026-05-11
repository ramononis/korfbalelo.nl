import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    idea
    application
}

fun JavaExec.forwardSystemProperties(vararg prefixes: String) {
    doFirst {
        System.getProperties().stringPropertyNames()
            .filter { key -> prefixes.any(key::startsWith) }
            .forEach { key ->
                systemProperty(key, System.getProperty(key))
            }
    }
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jgrapht:jgrapht-core:1.5.3")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-gson:2.3.1")
    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation(kotlin("test"))

}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("nl.korfbalelo.elo.ApplicationNew") // Set the main class here
}

tasks.named<JavaExec>("run") {
    forwardSystemProperties("elo.scraper.")
}
// Add a new task for running the alternative class
tasks.register<JavaExec>("predict") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("nl.korfbalelo.elo.SeasonPredicter") // Replace with your other class name
    forwardSystemProperties("elo.predict.", "elo.scraper.")
}

tasks.register<JavaExec>("scrape") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("nl.korfbalelo.mijnkorfbal.Scraper")
    forwardSystemProperties("elo.scraper.")
}

tasks.register<JavaExec>("benchmarkScoreRating") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("nl.korfbalelo.elo.ScoreRatingBenchmark")
    forwardSystemProperties("elo.model.", "elo.benchmark.")
}

tasks.test {
    useJUnitPlatform()
}
