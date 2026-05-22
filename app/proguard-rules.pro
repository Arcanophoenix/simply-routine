# Room — keep entity and DAO classes
-keep class com.simplyroutine.data.** { *; }

# Glance widget — keep all widget receivers and content providers
-keep class androidx.glance.** { *; }
-keep class com.simplyroutine.widget.** { *; }

# Kotlin serialization / coroutines
-keepnames class kotlinx.coroutines.** { *; }

# Keep R classes (resource references from RemoteViews / Glance)
-keepclassmembers class **.R$* { public static <fields>; }

# Suppress warnings for unused platform classes
-dontwarn java.lang.instrument.**
