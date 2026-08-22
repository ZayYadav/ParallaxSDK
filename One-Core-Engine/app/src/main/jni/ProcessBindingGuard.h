#pragma once

/**
 * Verifies that the APK being attested is the real installed base.apk associated with the
 * currently executing libParallaxLoader.so, rather than an embedded/extracted copy supplied by
 * Java or a wrapper. When strict_runtime_scan is true, writable secondary APK/DEX/JAR sources are
 * also rejected for the sensitive licensing path.
 */
bool onecore_verify_process_bound_apk(
        const char *claimed_apk_path,
        const char *package_name,
        bool strict_runtime_scan);
