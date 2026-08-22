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

    // The Loader intentionally hosts/virtualizes guest code from its private storage. Treating
    // every writable APK/DEX/JAR mapping as a signature failure makes the genuine signed Loader
    // reject its own legitimate runtime. Keep the strong process-bound host verification here:
    // the claimed file must still be the real installed base.apk derived from the executing
    // libParallaxLoader.so, nested original/backup APK payloads are still rejected, the compiled
    // signer must match, APK v2 signed data/content digests must verify, and native text remains
    // bound. Guest-code map scanning is therefore deliberately disabled for this host check.
    const bool verified = onecore_verify_process_bound_apk(path, package_value, false);
    env->ReleaseStringUTFChars(apkPath, path);
    env->ReleaseStringUTFChars(packageName, package_value);
    return verified ? JNI_TRUE : JNI_FALSE;
}
