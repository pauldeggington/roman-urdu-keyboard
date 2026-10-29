# Used by the -Pminify build (R8), which is what CI publishes as the main APK.
# LiteRT-LM is reached from native code, so its classes must keep their names and members.
-keep class com.google.ai.edge.** { *; }
-keep class com.google.protobuf.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.paul.urdukb.** { *; }   # first trial: shrink the libraries only; our own code stays as written
-dontwarn **
