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

    // Sensitive license verification is the strict path: the claimed APK must be the real
    // installed base.apk bound to the currently executing native library, and writable dynamic
    // code sources / embedded original-APK payloads are rejected.
    const bool verified = onecore_verify_process_bound_apk(path, package_value, true);
    env->ReleaseStringUTFChars(apkPath, path);
    env->ReleaseStringUTFChars(packageName, package_value);
    return verified ? JNI_TRUE : JNI_FALSE;
}
