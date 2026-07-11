# Proguard / R8 rules for Mantra Siddhi (Anushtanam).
# R8 runs in full mode (AGP 8 default) on release builds, so reflection-dependent
# libraries need explicit keeps. Failures from under-keeping surface at runtime, not
# build time — exercise a release build (DB read/write, a Hilt-injected screen) after
# changing these.

# --- Kotlin metadata & generics (coroutines, data classes, reflection) ---
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,Exceptions

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class **_Impl { *; }
-dontwarn androidx.room.paging.**

# --- Dagger Hilt ---
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *
-keep class dagger.hilt.** { *; }
-keep class **_HiltModules* { *; }
-dontwarn dagger.hilt.**

# --- App entities/models accessed across DI / Room boundaries ---
-keep class com.mantrasiddhi.data.local.entity.** { *; }
