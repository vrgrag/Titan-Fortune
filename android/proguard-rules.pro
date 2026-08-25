-dontusemixedcaseclassnames
-verbose

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** { volatile <fields>; }
