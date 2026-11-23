pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TunnelSlide3D"

// core    - all game code, platform independent (Kotlin/JVM)
// android - Android launcher (primary target)
// lwjgl3  - desktop launcher, used for the fast dev loop
// web     - browser build through TeaVM (JavaScript / WebAssembly)
include(":core", ":android", ":lwjgl3", ":web")
