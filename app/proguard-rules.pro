# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Project specific ProGuard rules for com.aqlanlab.app

# Keep Room entities, DAOs, and Database
-keep class com.aqlanlab.app.data.models.** { *; }
-keep class com.aqlanlab.app.data.dao.** { *; }
-keep class com.aqlanlab.app.data.AppDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Moshi models & codegen adapters
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep class com.squareup.moshi.** { *; }
-keep class * implements com.squareup.moshi.JsonAdapter { *; }

# Keep Network models & Services
-keep class com.aqlanlab.app.data.network.** { *; }
-keep class com.aqlanlab.app.network.** { *; }

# Keep Firebase App Check & Auth models
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.auth.** { *; }

# Keep Line numbers for crash reports
-keepattributes SourceFile,LineNumberTable

