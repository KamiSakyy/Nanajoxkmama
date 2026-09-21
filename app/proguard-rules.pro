# AniBeat — keep media-session service and models used via reflection/serialization
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
