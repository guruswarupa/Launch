

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}


-keep,allowoptimization class com.guruswarupa.launch.models.** { *; }

-keep,allowoptimization class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

-keep,allowoptimization class androidx.biometric.** { *; }

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

# Local file transfer: self-signed certificate generation uses BouncyCastle via JCA lookups.
-keep,allowoptimization class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# MediaPipe Tasks GenAI (on-device LLM assistant) — JNI + protobuf rely on reflection.
-keep,allowoptimization class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep,allowoptimization class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.fontbox.**
-keep,allowoptimization class com.tom_roush.pdfbox.** { *; }
-keep,allowoptimization class com.tom_roush.fontbox.** { *; }
