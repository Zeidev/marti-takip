# ---------------------------------------------------------------------------
# Martı Takip - ProGuard/R8 rules
# ---------------------------------------------------------------------------

# Room generates implementations at compile time; the entities/DAOs are
# referenced reflectively only through generated code, keep the public API.
-keep class com.martitakip.app.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# osmdroid uses reflection for the tile provider / cache manager factories.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Google Play Services location.
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# Kotlin metadata and coroutines internals.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Serialization of enum values inside the report / share intents.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
