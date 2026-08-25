-dontusemixedcaseclassnames
-verbose

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** { volatile <fields>; }

-keep class com.tf.aurora.** { *; }
-keep class com.titanfortune.game.** { *; }
-keep class com.tf.aurora.mask.Relic { *; }
-keep class com.appsflyer.** { *; }
-dontwarn com.appsflyer.**
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
