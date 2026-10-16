# EventFinder proguard rules (prototype: shrinking disabled, rules kept for release builds)
-keepattributes *Annotation*

# Retrofit / Gson reflection
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class com.eventfinder.app.data.remote.dto.** { *; }

# Firebase (Firestore / Storage / FCM) ships its own consumer rules; keep
# our mapping helpers so ProGuard never strips the Firestore document maps.
-keep class com.eventfinder.app.data.cloud.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }