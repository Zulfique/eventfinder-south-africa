# EventFinder proguard rules (prototype: shrinking disabled, rules kept for release builds)
-keepattributes *Annotation*

# Retrofit / Gson reflection
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class com.eventfinder.app.data.remote.dto.** { *; }