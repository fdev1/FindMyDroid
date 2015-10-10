# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/fernan/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

#-keep public class com.google.android.gms.**
#-dontwarn com.google.android.gms.**

-keep class com.google.android.gms.internal.zzacc
-keepclassmembers class com.google.android.gms.internal.zzw {
    addHeader(java.lang.String,java.lang.String);
    setURI(java.net.URI);
}
#-dontwarn com.google.android.gms.internal.zzac
#-dontwarn com.google.android.gms.internal.ssw
