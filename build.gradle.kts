plugins {
    kotlin("jvm") version "2.2.10"
    id("org.graalvm.buildtools.native") version "0.9.8"}

group = "de.till1993"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("arw_cleanup") // The name of the native image, defaults to the project name
            mainClass.set("de.till1993.MainKt")
        }
    }
}
