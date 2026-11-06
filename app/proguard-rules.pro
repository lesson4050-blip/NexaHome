# ProGuard rules for Smart TV Launcher (1GB RAM optimization)
-keepattributes *Annotation*
-dontwarn okio.**
-dontwarn coil.**
-keep class com.orbit.tv.launcher.platform.** { *; }
-keep class com.orbit.tv.launcher.core.** { *; }
