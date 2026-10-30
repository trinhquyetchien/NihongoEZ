# PyTorch Lite classes and JNI dependencies
-keep class org.pytorch.** { *; }
-keepclassmembers class org.pytorch.** { *; }
-keep class com.facebook.jni.** { *; }
-keepclassmembers class com.facebook.jni.** { *; }

# Keep Kanji Data classes
-keep class com.example.kanji.** { *; }
