#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <dirent.h>
#include <obfuscate.h>
#include <sys/stat.h>
#include <openssl/crypto.h>
#include <openssl/sha.h>
#include "backends/ModsLoader.h"
#include "ESP.h"
#include "Hacks.h"

ESP espOverlay;
int type = 1, utype = 2;

namespace {
constexpr int GUARD_PACKAGE_MISMATCH = 1 << 0;
constexpr int GUARD_TRACED = 1 << 1;
constexpr int GUARD_INJECTED_MAP = 1 << 2;
constexpr int GUARD_SUSPICIOUS_THREAD = 1 << 3;
constexpr int GUARD_PROXY = 1 << 4;
constexpr int GUARD_BAD_PINS = 1 << 5;
constexpr int GUARD_BAD_PUBLIC_KEY = 1 << 6;
constexpr int GUARD_WRITABLE_EXEC = 1 << 7;
constexpr int GUARD_PRELOAD = 1 << 8;

std::string lower_copy(std::string value) {
    for (char &ch : value) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }
    return value;
}

bool contains_any(const std::string &value, const std::vector<std::string> &needles) {
    const std::string lowered = lower_copy(value);
    for (const auto &needle : needles) {
        if (lowered.find(needle) != std::string::npos) {
            return true;
        }
    }
    return false;
}

bool tracer_present() {
    std::ifstream status("/proc/self/status");
    if (!status.is_open()) return false;
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("TracerPid:", 0) == 0) {
            std::istringstream parser(line.substr(10));
            int tracer = 0;
            parser >> tracer;
            return tracer > 0;
        }
    }
    return false;
}

int inspect_maps() {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return 0;

    const std::vector<std::string> suspicious = {
            "frida", "gadget", "libsubstrate", "substrate",
            "libxposed", "xposed", "zygisk", "riru", "edxp",
            "lsposed", "sandhook", "yahfa"
    };

    int result = 0;
    std::string line;
    while (std::getline(maps, line)) {
        if (contains_any(line, suspicious)) {
            result |= GUARD_INJECTED_MAP;
        }
        if (line.find("libParallaxLoader.so") != std::string::npos) {
            std::istringstream parser(line);
            std::string range;
            std::string permissions;
            parser >> range >> permissions;
            if (permissions.find('w') != std::string::npos
                    && permissions.find('x') != std::string::npos) {
                result |= GUARD_WRITABLE_EXEC;
            }
        }
    }
    return result;
}

bool suspicious_threads_present() {
    DIR *directory = opendir("/proc/self/task");
    if (directory == nullptr) return false;

    const std::vector<std::string> suspicious = {
            "gum-js-loop", "gmain", "frida", "pool-frida", "xposed", "substrate"
    };
    bool found = false;
    struct dirent *entry = nullptr;
    while ((entry = readdir(directory)) != nullptr && !found) {
        const char *name = entry->d_name;
        if (name == nullptr || !std::isdigit(static_cast<unsigned char>(name[0]))) continue;
        std::string path = std::string("/proc/self/task/") + name + "/comm";
        std::ifstream comm(path);
        std::string threadName;
        if (comm.is_open() && std::getline(comm, threadName)
                && contains_any(threadName, suspicious)) {
            found = true;
        }
    }
    closedir(directory);
    return found;
}

bool valid_pin_string(const std::string &pin) {
    if (pin.size() != 51 || pin.rfind("sha256/", 0) != 0) return false;
    for (size_t i = 7; i < pin.size(); ++i) {
        const unsigned char ch = static_cast<unsigned char>(pin[i]);
        const bool ok = std::isalnum(ch) || ch == '+' || ch == '/' || ch == '=';
        if (!ok) return false;
    }
    return pin.back() == '=';
}

bool valid_pins(JNIEnv *env, jobjectArray pins) {
    if (pins == nullptr) return false;
    const jsize count = env->GetArrayLength(pins);
    if (count <= 0 || count > 8) return false;
    for (jsize i = 0; i < count; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(pins, i));
        if (item == nullptr) return false;
        const char *raw = env->GetStringUTFChars(item, nullptr);
        if (raw == nullptr) {
            env->DeleteLocalRef(item);
            return false;
        }
        const std::string pin(raw);
        env->ReleaseStringUTFChars(item, raw);
        env->DeleteLocalRef(item);
        if (!valid_pin_string(pin)) return false;
    }
    return true;
}

bool valid_public_key(JNIEnv *env, jstring publicKey) {
    if (publicKey == nullptr) return false;
    const char *raw = env->GetStringUTFChars(publicKey, nullptr);
    if (raw == nullptr) return false;
    const std::string value(raw);
    env->ReleaseStringUTFChars(publicKey, raw);
    if (value.size() < 256 || value.size() > 2048) return false;
    for (char ch : value) {
        const unsigned char c = static_cast<unsigned char>(ch);
        if (!(std::isalnum(c) || c == '+' || c == '/' || c == '=')) {
            return false;
        }
    }
    return true;
}

bool package_matches(JNIEnv *env, jstring packageName) {
    if (packageName == nullptr) return false;
    const char *actual = env->GetStringUTFChars(packageName, nullptr);
    if (actual == nullptr) return false;
    const bool match = std::strcmp(actual, OBFUSCATE("com.onecore.loader")) == 0;
    env->ReleaseStringUTFChars(packageName, actual);
    return match;
}
}

extern "C"
JNIEXPORT jstring JNICALL
com_onecore_loader_activity_LoginActivity_GetKey(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("https://t.me/ParallaxOwner"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_onecore_loader_security_NativeLicenseGuard_nativeConnectUrl(
        JNIEnv *env, jclass) {
    return env->NewStringUTF(OBFUSCATE(
            "https://parallaxloader.parallaxserver.online/api/v2/connect"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_onecore_loader_security_NativeLicenseGuard_nativeConnectHost(
        JNIEnv *env, jclass) {
    return env->NewStringUTF(OBFUSCATE("parallaxloader.parallaxserver.online"));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_onecore_loader_security_NativeLicenseGuard_nativeCheckEnvironment(
        JNIEnv *env,
        jclass,
        jstring packageName,
        jobjectArray tlsPins,
        jstring publicKeyB64,
        jboolean proxyConfigured,
        jboolean debuggerConnected) {
    int result = 0;
    if (!package_matches(env, packageName)) {
        result |= GUARD_PACKAGE_MISMATCH;
    }
    if (debuggerConnected == JNI_TRUE || tracer_present()) {
        result |= GUARD_TRACED;
    }
    result |= inspect_maps();
    if (suspicious_threads_present()) {
        result |= GUARD_SUSPICIOUS_THREAD;
    }
    if (proxyConfigured == JNI_TRUE) {
        result |= GUARD_PROXY;
    }
    if (!valid_pins(env, tlsPins)) {
        result |= GUARD_BAD_PINS;
    }
    if (!valid_public_key(env, publicKeyB64)) {
        result |= GUARD_BAD_PUBLIC_KEY;
    }
    const char *preload = std::getenv("LD_PRELOAD");
    if (preload != nullptr && preload[0] != '\0') {
        result |= GUARD_PRELOAD;
    }
    return static_cast<jint>(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_loader_security_NativeSigningVerifier_verifySigningIdentity(
        JNIEnv *env,
        jclass,
        jobjectArray allowedDigests,
        jobjectArray certificates,
        jstring actualPackage,
        jstring expectedPackage) {
    if (allowedDigests == nullptr || certificates == nullptr
            || actualPackage == nullptr || expectedPackage == nullptr) {
        return JNI_FALSE;
    }

    const char *actual = env->GetStringUTFChars(actualPackage, nullptr);
    const char *expected = env->GetStringUTFChars(expectedPackage, nullptr);
    if (actual == nullptr || expected == nullptr) {
        if (actual != nullptr) env->ReleaseStringUTFChars(actualPackage, actual);
        if (expected != nullptr) env->ReleaseStringUTFChars(expectedPackage, expected);
        return JNI_FALSE;
    }
    const bool packageMatches = std::strcmp(actual, expected) == 0;
    env->ReleaseStringUTFChars(actualPackage, actual);
    env->ReleaseStringUTFChars(expectedPackage, expected);
    if (!packageMatches) {
        return JNI_FALSE;
    }

    const jsize allowedCount = env->GetArrayLength(allowedDigests);
    const jsize certificateCount = env->GetArrayLength(certificates);
    if (allowedCount <= 0 || certificateCount <= 0) {
        return JNI_FALSE;
    }

    std::vector<std::vector<unsigned char>> allowed;
    allowed.reserve(static_cast<size_t>(allowedCount));
    for (jsize index = 0; index < allowedCount; ++index) {
        auto digestArray = static_cast<jbyteArray>(
                env->GetObjectArrayElement(allowedDigests, index));
        if (digestArray == nullptr || env->GetArrayLength(digestArray) != SHA256_DIGEST_LENGTH) {
            if (digestArray != nullptr) env->DeleteLocalRef(digestArray);
            return JNI_FALSE;
        }
        std::vector<unsigned char> digest(SHA256_DIGEST_LENGTH);
        env->GetByteArrayRegion(
                digestArray,
                0,
                SHA256_DIGEST_LENGTH,
                reinterpret_cast<jbyte *>(digest.data()));
        env->DeleteLocalRef(digestArray);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return JNI_FALSE;
        }
        allowed.push_back(std::move(digest));
    }

    for (jsize index = 0; index < certificateCount; ++index) {
        auto certificateArray = static_cast<jbyteArray>(
                env->GetObjectArrayElement(certificates, index));
        if (certificateArray == nullptr) {
            return JNI_FALSE;
        }
        const jsize certificateLength = env->GetArrayLength(certificateArray);
        if (certificateLength <= 0) {
            env->DeleteLocalRef(certificateArray);
            return JNI_FALSE;
        }

        std::vector<unsigned char> certificate(static_cast<size_t>(certificateLength));
        env->GetByteArrayRegion(
                certificateArray,
                0,
                certificateLength,
                reinterpret_cast<jbyte *>(certificate.data()));
        env->DeleteLocalRef(certificateArray);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return JNI_FALSE;
        }

        unsigned char actualDigest[SHA256_DIGEST_LENGTH];
        if (SHA256(certificate.data(), certificate.size(), actualDigest) == nullptr) {
            return JNI_FALSE;
        }

        bool signerAllowed = false;
        for (const auto &allowedDigest : allowed) {
            signerAllowed |= CRYPTO_memcmp(
                    actualDigest,
                    allowedDigest.data(),
                    SHA256_DIGEST_LENGTH) == 0;
        }
        OPENSSL_cleanse(actualDigest, sizeof(actualDigest));
        OPENSSL_cleanse(certificate.data(), certificate.size());
        if (!signerAllowed) {
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_Overlay_DrawOn(JNIEnv * env, jclass, jobject espView, jobject canvas) {
    espOverlay = ESP(env, espView, canvas);
    if (espOverlay.isValid()) {
        DrawESP(espOverlay, espOverlay.getWidth(), espOverlay.getHeight());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_Overlay_Close(JNIEnv *, jobject) {
    Close();
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_SettingValue(JNIEnv *, jobject, jint code, jboolean jboolean1) {
    switch ((int)code) {
    case 2:
        isPlayerTeamID = jboolean1;
        break;
    case 3:
        isPlayerDist = jboolean1;
        break;
    case 4:
        isPlayerHealth = jboolean1;
        break;
    case 5:
        isPlayerName = jboolean1;
        break;
    case 6:
        isPlayerHead = jboolean1;
        break;
    case 7:
        isr360Alert = jboolean1;
        break;
    case 8:
        isSkelton = jboolean1;
        break;
    case 9:
        isGrenadeWarning = jboolean1;
        break;
    case 10:
        isEnemyWeapon = jboolean1;
        break;
    case 11:
        if (jboolean1 != 0)
            options.openState = 0;
        else
            options.openState = -1;
        break;
    case 12:
        options.tracingStatus = jboolean1;
        break;
    case 13:
        options.pour = jboolean1;
        break;
    case 14:
        options.isMetroMode = jboolean1;
        break;
    case 15:
        options.isRadar = jboolean1;
        break;
    }
}
/*
extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_SettingMemory(JNIEnv *, jobject, jint code, jboolean is_true) {
    switch ((int)code) {
    case 1:
        request.memory.LessRecoil = is_true;
        break;
    case 2:
        request.memory.InstantHit = is_true;
        break;
    case 4:
        request.memory.SmallCrosshair = is_true;
        break;
    case 5:
        request.memory.ZeroRecoil = is_true;
        break;
    case 6:
        request.memory.FastParachute = is_true;
        break;
    case 7:
        request.memory.NoShake = is_true;
        break;
    case 8:
        request.memory.HitEffect = is_true;
        break;
    }
}
*/
extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_SettingValueI(JNIEnv *, jobject, jint code, jint number) {
    switch ((int)code) {
    case 1:
        isPlayerBox = number;
        break;
    case 2:
        isPlayerLine = number;
        break;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatAim_AimbotFOV(JNIEnv *, jclass, jboolean isTrue)
{
    if (isTrue)
        options.openState = 0;
    else
        options.openState = -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_WideView(JNIEnv *, jobject, jint view) {
    options.wideView = view;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_Range(JNIEnv *, jobject, jint range) {
    options.aimingRange = 1 + range;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_distances(JNIEnv *, jobject, jint distances) {
    //options.aimingDistances=distances;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_recoil(JNIEnv *env, jobject thiz, jint recoil) {
    //options.RecoilCompt = recoil;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_Target(JNIEnv *, jobject, jint target) {
    options.aimbotmode = target;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_floating_FloatLogo_AimBy(JNIEnv *, jobject, jint aimby) {
    options.priority = aimby;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_loader_floating_Overlay_getReady(JNIEnv *, jobject) {
    int sockCheck = 1;
    if (!Create()) {
        perror("Creation failed");
        return false;
    }

    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &sockCheck, sizeof(int));
    if (!Bind()) {
        perror("Bind failed");
        return false;
    }

    if (!Listen()) {
        perror("Listen failed");
        return false;
    }

    if (Accept()) {
        return true;
    }
    return false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_onecore_loader_BoxApplication_BoxApp(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(OBFUSCATE("PARALLAX"));
}
