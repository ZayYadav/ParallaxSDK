#pragma once

/**
 * Verifies the exact installed APK without trusting Android PackageManager signer metadata.
 * The implementation parses APK Signature Scheme v2 directly, validates the signer certificate
 * against the signing digest compiled into this native library, verifies the signed-data signature,
 * recomputes the APK content digest, and checks this native library's executable mapping against
 * its on-disk ELF image.
 */
bool onecore_verify_installed_apk(const char *apk_path);

/** Returns true only when libKESHAVXOWNERLoader executable PT_LOAD bytes match the on-disk ELF. */
bool onecore_verify_native_text_integrity();
