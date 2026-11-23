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

// One runnable jar with all natives (Linux/Windows/macOS): ./gradlew :lwjgl3:dist
tasks.register<Jar>("dist") {
    group = "distribution"
    archiveFileName.set("TunnelSlide3D-desktop-${rootProject.findProperty("appVersion") ?: "dev"}.jar")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
}
