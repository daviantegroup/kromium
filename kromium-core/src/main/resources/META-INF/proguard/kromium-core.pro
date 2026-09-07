# Preserve JCEF native JNI classes, callbacks, and native methods
-keep class org.cef.** { *; }
-keepclassmembers class org.cef.** {
    native <methods>;
    <fields>;
}

# Preserve Kromium public API and JVM module opener reflection
-keep class dev.daviante.kromium.core.util.JvmModuleOpener { *; }
-keep class dev.daviante.kromium.presentation.** { *; }
-keep class dev.daviante.kromium.domain.** { *; }

# Preserve JAWT and internal AWT components used during native rendering
-keep class sun.awt.** { *; }
-keep class java.awt.** { *; }
-dontwarn sun.misc.Unsafe
