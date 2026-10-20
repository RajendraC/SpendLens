# SQLCipher calls into these classes from native code.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Keep line numbers for readable crash traces (no crash reporting SDK is used; traces stay on the device).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
