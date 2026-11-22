plugins {
    alias(libs.plugins.android.application)
}

val gdxVersion: String = libs.versions.gdx.get()
val abis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")

// One configuration per ABI so each natives jar lands in its own jniLibs folder.
val nativeConfigs = abis.associateWith { abi ->
    configurations.create("gdxNatives_${abi.replace('-', '_')}")
}
nativeConfigs.forEach { (abi, cfg) ->
    dependencies.add(cfg.name, "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
    dependencies.add(cfg.name, "com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
}

val nativesDir = layout.buildDirectory.dir("gdx-natives")

val copyAndroidNatives = tasks.register<Copy>("copyAndroidNatives") {
    into(nativesDir)
    nativeConfigs.forEach { (abi, cfg) ->
        from(cfg.elements.map { files -> files.map { zipTree(it) } }) {
            include("*.so")
            into(abi)
        }
    }
}

android {
    namespace = "io.github.iampopovich.tunnelslide"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.iampopovich.tunnelslide"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(rootProject.file("assets").path)
            jniLibs.directories.add(nativesDir.get().asFile.path)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(copyAndroidNatives) }

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
}
