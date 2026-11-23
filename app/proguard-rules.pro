# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.quern.app.domain.model.**$$serializer { *; }
-keepclassmembers class com.quern.app.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.quern.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.quern.app.data.sync.**$$serializer { *; }
-keepclassmembers class com.quern.app.data.sync.** {
    *** Companion;
}
-keepclasseswithmembers class com.quern.app.data.sync.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Media3 / Room safe rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }
-keep class com.quern.app.data.local.** { *; }
