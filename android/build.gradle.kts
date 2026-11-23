import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing: keystore.properties in the repo root (gitignored) or the same keys as
// environment variables (CI). Without them the release APK is built unsigned (F-Droid signs
// its own builds). -PsignWithDebug=true signs release with the debug key for local testing.
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Blank values count as missing (CI passes empty strings when secrets are not set).
fun signingValue(key: String): String? =
    (signingProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }
val releaseStore: String? = signingValue("TS_KEYSTORE")

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
        versionCode = 10
        versionName = providers.gradleProperty("appVersion").get()
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = rootProject.file(releaseStore)
                storePassword = signingValue("TS_KEYSTORE_PASSWORD")
                keyAlias = signingValue("TS_KEY_ALIAS")
                keyPassword = signingValue("TS_KEY_PASSWORD")
            }
        }
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
            // R8 shrinks the APK; libGDX needs a few keep rules (proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                releaseStore != null -> signingConfigs.getByName("release")
                providers.gradleProperty("signWithDebug").orNull == "true" -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    // Reproducible builds for F-Droid: no build-time dependency metadata blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(copyAndroidNatives) }

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
}
