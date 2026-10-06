# Stilla has no reflection-based libraries yet, so the default rules are enough.
# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
