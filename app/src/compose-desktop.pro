-keep class org.cef.** { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory

# DataStore preferences: the bundled protobuf-lite resolves message fields by name,
# so shrinking must keep them (mirrors the library's own consumer rules).
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
