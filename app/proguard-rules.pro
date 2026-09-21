# AniBeat R8 rules — bulletproof transport + models
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# OkHttp / OkIO — keep everything (transport is mission-critical)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# JSON + models + cache policy
-keep class org.json.** { *; }
-keep class com.kamisakyy.nanajoxkmama.core.model.** { *; }
-keep class com.kamisakyy.nanajoxkmama.core.network.HttpCachePolicy$Policy { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
