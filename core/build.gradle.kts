import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.gdx.core)
    api(libs.gdx.freetype)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

// Renders the soundtrack to WAV files (core/build/music-preview) to listen to it offline.
tasks.register<JavaExec>("musicPreview") {
    group = "verification"
    description = "Renders the procedural soundtrack to WAV files"
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.iampopovich.tunnelslide.audio.MusicPreviewKt")
    args(layout.buildDirectory.dir("music-preview").get().asFile.absolutePath)
}
