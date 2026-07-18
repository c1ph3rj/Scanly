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

# LiteRT resolves parts of its runtime through reflection and JNI.
# Keep the runtime package intact so release builds can still create interpreters.
-keep class org.tensorflow.lite.** { *; }

# PdfBox-Android exposes optional JPEG 2000 support through JP2Android. Scanly only
# encrypts PDFs that it renders from Android bitmaps, so those optional codecs are
# intentionally not bundled.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# The QR flow creates ML Kit's barcode client and CameraX's provider from the
# release APK. Keep their reflective/native entry points available after R8.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.android.datatransport.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class androidx.camera.** { *; }
-keep class org.opencv.** { *; }
