# ProGuard / R8 rules for Orbit TV Launcher (Optimized for 1GB RAM Android TV hardware)

# Preserve annotations and line numbers for stack traces
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep data models used across UI and JSON parsing
-keep class com.orbit.tv.launcher.features.home.model.** { *; }
-keep class com.orbit.tv.launcher.features.iptv.model.** { *; }
-keep class com.orbit.tv.launcher.features.vpn.model.** { *; }
-keep class com.orbit.tv.launcher.platform.** { *; }
-keep class com.orbit.tv.launcher.core.** { *; }

# Keep Room Database entities and DAOs
-keep class androidx.room.** { *; }
-keep class com.orbit.tv.launcher.features.iptv.data.** { *; }
-keep class com.orbit.tv.launcher.features.home.data.** { *; }
-dontwarn androidx.room.paging.**

# Keep Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil & Coroutines
-dontwarn okio.**
-dontwarn coil.**
-keep class kotlinx.coroutines.** { *; }

# Keep ZXing QR Code generator
-keep class com.google.zxing.** { *; }

# Keep Javascript Interface for Arcade WebViews
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Android TV Leanback
-keep class androidx.leanback.** { *; }

# Keep Custom Views for XML inflation
-keep class com.orbit.tv.launcher.ui.views.** { *; }
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
