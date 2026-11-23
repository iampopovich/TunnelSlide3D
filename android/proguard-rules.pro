# libGDX: classes created by reflection or called from native code.
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-keep class com.badlogic.gdx.backends.android.** { *; }
-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* {
    <init>(com.badlogic.gdx.Application, android.content.Context, java.lang.Object, com.badlogic.gdx.backends.android.AndroidApplicationConfiguration);
}
# Native methods (gdx core, freetype) are looked up by name.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
-keep class com.badlogic.gdx.utils.SharedLibraryLoader { *; }
-keep class com.badlogic.gdx.utils.BufferUtils { *; }
# Preferences/JSON reflection is not used by the game; keep stack traces readable.
-keepattributes SourceFile,LineNumberTable
