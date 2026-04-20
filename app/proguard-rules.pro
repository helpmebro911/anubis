# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# AIDL UserService (runs in Shizuku's process)
-keep class sgnv.anubis.app.IUserService { *; }
-keep class sgnv.anubis.app.IUserService$* { *; }
-keep class sgnv.anubis.app.shizuku.UserService { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Room TypeConverters
-keep class sgnv.anubis.app.data.db.AppGroupConverter { *; }

# Tink (pulled in by androidx.security:security-crypto for EncryptedSharedPreferences).
# These annotation packages are compile-only — absent at runtime. R8 otherwise fails.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
