#include <jni.h>

#include "ProcessBindingGuard.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_loader_security_NativeLicenseGuard_nativeVerifyInstalledApk(
        JNIEnv *env,
        jclass,
        jstring apkPath,
        jstring packageName) {
    if (apkPath == nullptr || packageName == nullptr) return JNI_FALSE;

    const char *path = env->GetStringUTFChars(apkPath, nullptr);
    const char *package_value = env->GetStringUTFChars(packageName, nullptr);
    if (path == nullptr || package_value == nullptr) {
        if (path != nullptr) env->ReleaseStringUTFChars(apkPath, path);
        if (package_value != nullptr) env->ReleaseStringUTFChars(packageName, package_value);
        return JNI_FALSE;
    }

    // License-time native attestation owns host/process binding only. The claimed file must be the
    // real installed base.apk derived from the currently executing Loader library; wrapper-style
    // embedded original/backup payloads are rejected. Cryptographic signer/content validation is
    // performed separately by AppIntegrity using apksig plus the build-pinned native certificate
    // digest. Runtime relocated native text is intentionally not treated as signing identity.
    const bool verified = onecore_verify_process_bound_apk(path, package_value, false);
    env->ReleaseStringUTFChars(apkPath, path);
    env->ReleaseStringUTFChars(packageName, package_value);
    return verified ? JNI_TRUE : JNI_FALSE;
}
