# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep entities and models
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }

# Moshi rules
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <init>(...);
}
-keep class com.squareup.moshi.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

