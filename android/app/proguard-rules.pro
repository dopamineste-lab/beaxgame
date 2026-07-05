# Socket.IO / engine.io rely on reflection for their transports.
-keep class io.socket.** { *; }
-keep class org.json.** { *; }
-dontwarn io.socket.**

# Keep Hilt-generated components.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
