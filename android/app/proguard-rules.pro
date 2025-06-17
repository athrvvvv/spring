# Don’t strip OkHttp, JSON, and reflection-based Kotlin/Flutter plugins
-keep class okhttp3.** { *; }
-keep class org.json.** { *; }
-keep class io.flutter.** { *; }
-keep class com.example.spring.** { *; }
-keep class com.google.android.play.** { *; }
-dontwarn com.google.android.play.**

# Keep all native method calls used by MethodChannel
-keep class io.flutter.plugin.common.MethodChannel { *; }
-keep class io.flutter.embedding.engine.FlutterEngine { *; }
-keep class com.example.spring.MainActivity { *; }