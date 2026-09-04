# TLS pin rotation

GitHub Actions release builds keep runtime OkHttp certificate pinning enabled and append the licensing host's current leaf SPKI pin only after the build JVM has successfully completed normal CA and hostname verification for `parallaxloader.parallaxserver.online`.

The configured `PARALLAX_TLS_PINS` values remain in the rotation set. If the live verified leaf pin is not already configured, CI logs a warning and embeds both the configured pins and the newly verified live pin in that build. Local builds continue to use the explicitly supplied Gradle/environment pin configuration.
