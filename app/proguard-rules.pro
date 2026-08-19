# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==========================================================================
# قواعد R8/ProGuard لتطبيق إدارة المعامل — مركز د. عقلان الكامل
# التصغير والتشويش مفعّلان في بناء الإصدار (isMinifyEnabled = true).
# ==========================================================================

# --- Room ---
-keep class androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- كيانات البيانات: تُسلسَل عبر Moshi بالانعكاس (KotlinJsonAdapterFactory) ---
-keep class com.example.data.models.** { *; }
-keep class com.example.network.CloudModels* { *; }
-keep class com.example.network.CloudBackupPayload { *; }
-keep class com.example.network.FirebaseStorageBackupInfo { *; }
-keep class com.example.network.FirestoreBackupSnapshot { *; }

# --- Moshi ---
-keepclasseswithmembers class * { @com.squareup.moshi.* <methods>; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn okio.**
-dontwarn com.squareup.moshi.**

# --- Kotlin reflection المستخدم من Moshi ---
-keep class kotlin.reflect.jvm.internal.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisibleAnnotations

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepnames class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- ML Kit / ZXing (قارئ QR) ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.zxing.** { *; }

# --- Credential Manager / Google ID ---
-keep class com.google.android.libraries.identity.googleid.** { *; }
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- إبقاء أرقام الأسطر لتتبع الأعطال في الإنتاج مع إخفاء اسم الملف الأصلي ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
