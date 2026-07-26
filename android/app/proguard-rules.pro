# P1 阶段不开启混淆；此处仅保留通用保留规则占位。
-keepattributes *Annotation*
-keep class com.creationreadingassistant.** { *; }

# Retrofit
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.creationreadingassistant.**$$serializer { *; }
-keepclassmembers class com.creationreadingassistant.** { *** Companion; }
-keepclasseswithmembers class com.creationreadingassistant.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
# P1 阶段不开启混淆；此处仅保留通用保留规则占位。
-keepattributes *Annotation*
-keep class com.creationreadingassistant.** { *; }
