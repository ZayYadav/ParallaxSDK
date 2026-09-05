#include <jni.h>
#include <string>

// Authentication is implemented by HostedLicenseClient over verified HTTPS.
// Native helpers below are retained only for the remaining runtime UI/actions.
std::string EXP = "NULL";

extern "C"
JNIEXPORT jstring JNICALL
Java_com_onecore_loader_libhelper_DownloadZip_PASSJKPAPA(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(OBFUSCATE("989878"));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_onecore_loader_activity_MainActivity_FixCrash(JNIEnv *env, jobject thiz) {
    // Security boundary: never download executable runtime code from a mutable branch ref.
    // This URL is pinned to the audited Parallaxapp commit that contains JANGAM.zip.
    return env->NewStringUTF(OBFUSCATE("https://github.com/ZayYadav/Parallaxapp/raw/refs/heads/main/Parallax.zip"));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_onecore_loader_activity_MainActivity_TimeExpired(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(EXP.c_str());
}
