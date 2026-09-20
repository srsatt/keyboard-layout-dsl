plugins {
    kotlin("jvm") version "2.4.20"
    `java-library`
}

group = "dev.srsatt.keyboard"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
