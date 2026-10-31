# PyTorch Lite classes and JNI dependencies
-keep class org.pytorch.** { *; }
-keepclassmembers class org.pytorch.** { *; }
-keep class com.facebook.jni.** { *; }
-keepclassmembers class com.facebook.jni.** { *; }

# Keep App Data classes & package
-keep class com.onis.kanji.** { *; }
-keepclassmembers class com.onis.kanji.** { *; }

# Keep Kuromoji Japanese Morphological Analyzer and its resources/reflection
-keep class com.atilika.kuromoji.** { *; }
-keepclassmembers class com.atilika.kuromoji.** { *; }

