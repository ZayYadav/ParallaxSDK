#include <jni.h>
#include <cstring>
#include <obfuscate.h>

#include "NativeApkAttestation.h"

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

    const bool package_ok = std::strcmp(package_value, OBFUSCATE("com.onecore.loader")) == 0;
    const bool verified = package_ok && onecore_verify_installed_apk(path);
    env->ReleaseStringUTFChars(apkPath, path);
    env->ReleaseStringUTFChars(packageName, package_value);
    return verified ? JNI_TRUE : JNI_FALSE;
}
