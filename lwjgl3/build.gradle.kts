import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Shared assets/ folder is served from the classpath on desktop.
sourceSets.main { resources.srcDir(rootProject.file("assets")) }

application {
    mainClass.set("io.github.iampopovich.tunnelslide.lwjgl3.Lwjgl3LauncherKt")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.lwjgl3)
    implementation("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:${libs.versions.gdx.get()}:natives-desktop")
}

tasks.named<JavaExec>("run") {
    // Pass TS_* debug env vars (screenshot, autopilot) straight through.
    environment(System.getenv().filterKeys { it.startsWith("TS_") })
}
