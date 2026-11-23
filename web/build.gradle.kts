import org.teavm.gradle.api.OptimizationLevel

// Browser build: TeaVM compiles the JVM bytecode of :core (Kotlin) to JavaScript / Wasm.
//   ./gradlew :web:gdx_teavm_web_js_run            dev server on http://localhost:8080
//   ./gradlew :web:gdx_teavm_web_js_release_build  -> web/build/dist/js/release/webapp
plugins {
    java
    id("com.github.xpenatan.gdx-teavm") version "1.6.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    implementation("com.github.xpenatan.gdx-teavm:backend-web:1.6.2")
    implementation("com.github.xpenatan.gdx-teavm:gdx-freetype-web:1.6.2")
}

gdxTeaVM {
    assets(rootProject.file("assets"))

    webDefaults {
        mainClass.set("io.github.iampopovich.tunnelslide.web.WebLauncher")
        htmlTitle.set("TunnelSlide3D")
    }

    js {
        optimization.set(OptimizationLevel.NONE)
    }

    js("release") {
        obfuscated.set(true)
        serverPort.set(8180)
        optimization.set(OptimizationLevel.BALANCED)
    }
}
