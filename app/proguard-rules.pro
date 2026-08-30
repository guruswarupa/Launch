

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}


-keep class com.guruswarupa.launch.models.** { *; }

-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

-keep class androidx.biometric.** { *; }

-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn javax.annotation.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

-keep public class * extends com.github.bumptech.glide.module.AppGlideModule
-keep public class * extends com.github.bumptech.glide.module.LibraryGlideModule
-keep class com.github.bumptech.glide.GeneratedAppGlideModuleImpl
-dontwarn com.github.bumptech.glide.load.resource.bitmap.VideoDecoder

-keepclassmembers class com.guruswarupa.launch.R$layout {
    public static ** *;
}

# Keep native method references
-keepclasseswithmembernames class * {
    native <methods>;
}

# MediaPipe Tasks GenAI (on-device LLM assistant) — JNI + protobuf rely on reflection.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
