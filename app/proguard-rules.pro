# v1 未开启 R8（isMinifyEnabled = false），此文件为将来开启时预留。

# ApkProvider 由 AndroidManifest 按类名实例化，不能被重命名。
-keep class com.obtainium.companion.ApkProvider { *; }

# Activity 同理（Manifest 中按类名引用）。
-keep class com.obtainium.companion.MainActivity { *; }
-keep class com.obtainium.companion.SettingsActivity { *; }
