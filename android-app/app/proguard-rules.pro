# CoreV ProGuard Rules

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep iText7
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Keep Gson
-keep class com.google.gson.** { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep domain models for serialization
-keep class com.corev.sysinfo.domain.model.** { *; }

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
