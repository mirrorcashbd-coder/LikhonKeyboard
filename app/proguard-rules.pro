# Likhon IME has no reflection-based dependencies. Keep these rules only to be
# explicit about what must survive R8 in release builds.
-keep class com.likhon.keyboard.LikhonIme { *; }
-keep class com.likhon.keyboard.SettingsActivity { *; }
