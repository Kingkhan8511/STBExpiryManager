# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entity classes
-keep class com.cableops.stbexpiry.data.model.** { *; }

# Keep Jsoup
-keep public class org.jsoup.** { *; }
-keeppackagenames org.jsoup.**

# Keep WorkManager workers
-keep class com.cableops.stbexpiry.worker.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Kotlin data classes used with reflection
-keepclassmembers class kotlin.Metadata { *; }
