# HomePilot ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# Gson
-keep class com.homepilot.app.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
