#include "SdkIdentityGuard.h"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <string>
#include <vector>

namespace {

constexpr jint GET_SIGNATURES_FLAG = 0x00000040;
constexpr jint GET_SIGNING_CERTIFICATES_FLAG = 0x08000000;

bool clearException(JNIEnv *env) {
    if (env == nullptr) return false;
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    return true;
}

std::string toString(JNIEnv *env, jstring value) {
    if (env == nullptr || value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        clearException(env);
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string upperAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::toupper(c));
    });
    return value;
}

bool isHex(const std::string &value, size_t exactLength) {
    if (value.size() != exactLength) return false;
    for (unsigned char c : value) {
        if (!std::isxdigit(c)) return false;
    }
    return true;
}

bool isToken(const std::string &value, size_t minLength, size_t maxLength) {
    if (value.size() < minLength || value.size() > maxLength) return false;
    for (unsigned char c : value) {
        if (!(std::isalnum(c) || c == '_' || c == '-')) return false;
    }
    return true;
}

bool isKeyId(const std::string &value) {
    if (value.empty() || value.size() > 64) return false;
    for (unsigned char c : value) {
        if (!(std::isalnum(c) || c == '_' || c == '-' || c == '.')) return false;
    }
    return true;
}

bool parsePositiveLong(const std::string &value, jlong *out) {
    if (out == nullptr || value.empty()) return false;
    for (unsigned char c : value) {
        if (!std::isdigit(c)) return false;
    }
    char *end = nullptr;
    const long long parsed = std::strtoll(value.c_str(), &end, 10);
    if (end == nullptr || *end != '\0' || parsed <= 0) return false;
    *out = static_cast<jlong>(parsed);
    return true;
}

int sdkInt(JNIEnv *env) {
    jclass versionClass = env->FindClass("android/os/Build$VERSION");
    if (versionClass == nullptr || !clearException(env)) return 0;
    jfieldID sdkField = env->GetStaticFieldID(versionClass, "SDK_INT", "I");
    if (sdkField == nullptr || !clearException(env)) {
        env->DeleteLocalRef(versionClass);
        return 0;
    }
    const jint value = env->GetStaticIntField(versionClass, sdkField);
    env->DeleteLocalRef(versionClass);
    return static_cast<int>(value);
}

bool sha256Hex(JNIEnv *env, jbyteArray bytes, std::string *out) {
    if (env == nullptr || bytes == nullptr || out == nullptr) return false;

    jclass digestClass = env->FindClass("java/security/MessageDigest");
    if (digestClass == nullptr || !clearException(env)) return false;
    jmethodID getInstance = env->GetStaticMethodID(
        digestClass, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    if (getInstance == nullptr || !clearException(env)) {
        env->DeleteLocalRef(digestClass);
        return false;
    }

    jstring algorithm = env->NewStringUTF("SHA-256");
    jobject digest = env->CallStaticObjectMethod(digestClass, getInstance, algorithm);
    env->DeleteLocalRef(algorithm);
    if (digest == nullptr || !clearException(env)) {
        env->DeleteLocalRef(digestClass);
        return false;
    }

    jmethodID digestMethod = env->GetMethodID(digestClass, "digest", "([B)[B");
    if (digestMethod == nullptr || !clearException(env)) {
        env->DeleteLocalRef(digest);
        env->DeleteLocalRef(digestClass);
        return false;
    }

    auto result = static_cast<jbyteArray>(env->CallObjectMethod(digest, digestMethod, bytes));
    if (result == nullptr || !clearException(env) || env->GetArrayLength(result) != 32) {
        if (result != nullptr) env->DeleteLocalRef(result);
        env->DeleteLocalRef(digest);
        env->DeleteLocalRef(digestClass);
        return false;
    }

    jbyte raw[32];
    env->GetByteArrayRegion(result, 0, 32, raw);
    if (!clearException(env)) {
        env->DeleteLocalRef(result);
        env->DeleteLocalRef(digest);
        env->DeleteLocalRef(digestClass);
        return false;
    }

    static const char HEX[] = "0123456789ABCDEF";
    std::string hex(64, '0');
    for (size_t i = 0; i < 32; ++i) {
        const unsigned int b = static_cast<unsigned char>(raw[i]);
        hex[i * 2] = HEX[(b >> 4) & 0x0F];
        hex[i * 2 + 1] = HEX[b & 0x0F];
    }
    *out = std::move(hex);

    env->DeleteLocalRef(result);
    env->DeleteLocalRef(digest);
    env->DeleteLocalRef(digestClass);
    return true;
}

bool packageInfoName(JNIEnv *env, jobject packageInfo, std::string *out) {
    if (env == nullptr || packageInfo == nullptr || out == nullptr) return false;
    jclass infoClass = env->GetObjectClass(packageInfo);
    if (infoClass == nullptr || !clearException(env)) return false;
    jfieldID field = env->GetFieldID(infoClass, "packageName", "Ljava/lang/String;");
    if (field == nullptr || !clearException(env)) {
        env->DeleteLocalRef(infoClass);
        return false;
    }
    auto value = static_cast<jstring>(env->GetObjectField(packageInfo, field));
    if (!clearException(env)) {
        env->DeleteLocalRef(infoClass);
        return false;
    }
    *out = toString(env, value);
    if (value != nullptr) env->DeleteLocalRef(value);
    env->DeleteLocalRef(infoClass);
    return !out->empty();
}

bool packageInfoSigner(JNIEnv *env, jobject packageInfo, int apiLevel, std::string *out) {
    if (env == nullptr || packageInfo == nullptr || out == nullptr) return false;
    jclass infoClass = env->GetObjectClass(packageInfo);
    if (infoClass == nullptr || !clearException(env)) return false;

    jobjectArray signatures = nullptr;
    if (apiLevel >= 28) {
        jfieldID signingInfoField = env->GetFieldID(
            infoClass, "signingInfo", "Landroid/content/pm/SigningInfo;");
        if (signingInfoField == nullptr || !clearException(env)) {
            env->DeleteLocalRef(infoClass);
            return false;
        }
        jobject signingInfo = env->GetObjectField(packageInfo, signingInfoField);
        if (signingInfo == nullptr || !clearException(env)) {
            env->DeleteLocalRef(infoClass);
            return false;
        }
        jclass signingInfoClass = env->GetObjectClass(signingInfo);
        jmethodID getCurrent = signingInfoClass == nullptr ? nullptr : env->GetMethodID(
            signingInfoClass, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
        if (getCurrent == nullptr || !clearException(env)) {
            if (signingInfoClass != nullptr) env->DeleteLocalRef(signingInfoClass);
            env->DeleteLocalRef(signingInfo);
            env->DeleteLocalRef(infoClass);
            return false;
        }
        signatures = static_cast<jobjectArray>(env->CallObjectMethod(signingInfo, getCurrent));
        const bool ok = clearException(env);
        env->DeleteLocalRef(signingInfoClass);
        env->DeleteLocalRef(signingInfo);
        if (!ok) {
            env->DeleteLocalRef(infoClass);
            return false;
        }
    } else {
        jfieldID signaturesField = env->GetFieldID(
            infoClass, "signatures", "[Landroid/content/pm/Signature;");
        if (signaturesField == nullptr || !clearException(env)) {
            env->DeleteLocalRef(infoClass);
            return false;
        }
        signatures = static_cast<jobjectArray>(env->GetObjectField(packageInfo, signaturesField));
        if (!clearException(env)) {
            env->DeleteLocalRef(infoClass);
            return false;
        }
    }
    env->DeleteLocalRef(infoClass);

    if (signatures == nullptr || env->GetArrayLength(signatures) != 1) {
        if (signatures != nullptr) env->DeleteLocalRef(signatures);
        return false;
    }

    jobject signature = env->GetObjectArrayElement(signatures, 0);
    env->DeleteLocalRef(signatures);
    if (signature == nullptr || !clearException(env)) return false;

    jclass signatureClass = env->GetObjectClass(signature);
    jmethodID toBytes = signatureClass == nullptr ? nullptr
        : env->GetMethodID(signatureClass, "toByteArray", "()[B");
    if (toBytes == nullptr || !clearException(env)) {
        if (signatureClass != nullptr) env->DeleteLocalRef(signatureClass);
        env->DeleteLocalRef(signature);
        return false;
    }

    auto certificate = static_cast<jbyteArray>(env->CallObjectMethod(signature, toBytes));
    const bool callOk = clearException(env);
    env->DeleteLocalRef(signatureClass);
    env->DeleteLocalRef(signature);
    if (!callOk || certificate == nullptr) {
        if (certificate != nullptr) env->DeleteLocalRef(certificate);
        return false;
    }

    const bool hashed = sha256Hex(env, certificate, out);
    env->DeleteLocalRef(certificate);
    return hashed;
}

void addPath(std::vector<std::string> *paths, const std::string &path) {
    if (paths == nullptr || path.empty()) return;
    if (std::find(paths->begin(), paths->end(), path) == paths->end()) {
        paths->push_back(path);
    }
}

void removePaths(std::vector<std::string> *paths, const std::vector<std::string> &remove) {
    if (paths == nullptr) return;
    paths->erase(
        std::remove_if(paths->begin(), paths->end(), [&](const std::string &value) {
            return std::find(remove.begin(), remove.end(), value) != remove.end();
        }),
        paths->end());
}

void addStringArray(JNIEnv *env, jobjectArray array, std::vector<std::string> *paths) {
    if (env == nullptr || array == nullptr || paths == nullptr) return;
    const jsize length = env->GetArrayLength(array);
    for (jsize i = 0; i < length; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        if (value != nullptr) {
            addPath(paths, toString(env, value));
            env->DeleteLocalRef(value);
        }
        if (!clearException(env)) return;
    }
}

bool readStringField(JNIEnv *env, jobject object, const char *name, std::string *out) {
    if (env == nullptr || object == nullptr || name == nullptr || out == nullptr) return false;
    jclass clazz = env->GetObjectClass(object);
    if (clazz == nullptr || !clearException(env)) return false;
    jfieldID field = env->GetFieldID(clazz, name, "Ljava/lang/String;");
    if (field == nullptr || !clearException(env)) {
        env->DeleteLocalRef(clazz);
        return false;
    }
    auto value = static_cast<jstring>(env->GetObjectField(object, field));
    if (!clearException(env)) {
        env->DeleteLocalRef(clazz);
        return false;
    }
    *out = toString(env, value);
    if (value != nullptr) env->DeleteLocalRef(value);
    env->DeleteLocalRef(clazz);
    return true;
}

void readStringArrayField(JNIEnv *env, jobject object, const char *name, std::vector<std::string> *paths) {
    if (env == nullptr || object == nullptr || name == nullptr || paths == nullptr) return;
    jclass clazz = env->GetObjectClass(object);
    if (clazz == nullptr || !clearException(env)) return;
    jfieldID field = env->GetFieldID(clazz, name, "[Ljava/lang/String;");
    if (field == nullptr || !clearException(env)) {
        env->DeleteLocalRef(clazz);
        return;
    }
    auto array = static_cast<jobjectArray>(env->GetObjectField(object, field));
    if (clearException(env) && array != nullptr) {
        addStringArray(env, array, paths);
        env->DeleteLocalRef(array);
    }
    env->DeleteLocalRef(clazz);
}

bool verifyArchive(JNIEnv *env,
                   jobject packageManager,
                   jmethodID getArchiveInfo,
                   const std::string &path,
                   jint flags,
                   int apiLevel,
                   const std::string &expectedPackage,
                   const std::string &expectedSigning,
                   bool mandatory,
                   bool *verified) {
    if (verified != nullptr) *verified = false;
    if (path.empty()) return !mandatory;

    jstring pathArg = env->NewStringUTF(path.c_str());
    jobject archiveInfo = env->CallObjectMethod(packageManager, getArchiveInfo, pathArg, flags);
    env->DeleteLocalRef(pathArg);

    const bool callOk = clearException(env);
    if (!callOk || archiveInfo == nullptr) {
        if (archiveInfo != nullptr) env->DeleteLocalRef(archiveInfo);
        return !mandatory;
    }

    std::string archiveName;
    if (!packageInfoName(env, archiveInfo, &archiveName) || archiveName != expectedPackage) {
        env->DeleteLocalRef(archiveInfo);
        return false;
    }

    std::string archiveSigning;
    const bool hasSigner = packageInfoSigner(env, archiveInfo, apiLevel, &archiveSigning);
    env->DeleteLocalRef(archiveInfo);
    if (!hasSigner) return !mandatory;
    if (upperAscii(archiveSigning) != expectedSigning) return false;

    if (verified != nullptr) *verified = true;
    return true;
}

std::vector<std::string> splitLines(const std::string &value) {
    std::vector<std::string> lines;
    size_t start = 0;
    while (true) {
        const size_t pos = value.find('\n', start);
        if (pos == std::string::npos) {
            lines.emplace_back(value.substr(start));
            break;
        }
        lines.emplace_back(value.substr(start, pos - start));
        start = pos + 1;
    }
    return lines;
}

bool verifyServerSignature(JNIEnv *env, jclass nativeClass, jstring canonical, jstring signature) {
    if (env == nullptr || nativeClass == nullptr || canonical == nullptr || signature == nullptr) return false;
    jmethodID verifyMethod = env->GetStaticMethodID(
        nativeClass,
        "verifyServerSignature",
        "(Ljava/lang/String;Ljava/lang/String;)Z");
    if (verifyMethod == nullptr || !clearException(env)) return false;
    const bool valid = env->CallStaticBooleanMethod(nativeClass, verifyMethod, canonical, signature) == JNI_TRUE;
    return clearException(env) && valid;
}

}  // namespace

jboolean verifyInstalledIdentity(JNIEnv *env, jclass,
                                 jobject context,
                                 jstring expectedPackageValue,
                                 jstring expectedSigningValue) {
    if (env == nullptr || context == nullptr || expectedPackageValue == nullptr || expectedSigningValue == nullptr) {
        return JNI_FALSE;
    }

    const std::string expectedPackage = toString(env, expectedPackageValue);
    const std::string expectedSigning = upperAscii(toString(env, expectedSigningValue));
    if (expectedPackage.empty() || !isHex(expectedSigning, 64)) return JNI_FALSE;

    const int apiLevel = sdkInt(env);
    if (apiLevel <= 0) return JNI_FALSE;
    const jint flags = apiLevel >= 28 ? GET_SIGNING_CERTIFICATES_FLAG : GET_SIGNATURES_FLAG;

    jclass contextClass = env->GetObjectClass(context);
    if (contextClass == nullptr || !clearException(env)) return JNI_FALSE;

    jmethodID getPackageName = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jmethodID getPackageManager = env->GetMethodID(
        contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jmethodID getApplicationInfo = env->GetMethodID(
        contextClass, "getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;");
    jmethodID getPackageCodePath = env->GetMethodID(contextClass, "getPackageCodePath", "()Ljava/lang/String;");
    jmethodID getPackageResourcePath = env->GetMethodID(contextClass, "getPackageResourcePath", "()Ljava/lang/String;");
    if (getPackageName == nullptr || getPackageManager == nullptr || getApplicationInfo == nullptr
        || getPackageCodePath == nullptr || getPackageResourcePath == nullptr || !clearException(env)) {
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    auto contextPackageValue = static_cast<jstring>(env->CallObjectMethod(context, getPackageName));
    const bool packageCallOk = clearException(env);
    const std::string contextPackage = toString(env, contextPackageValue);
    if (contextPackageValue != nullptr) env->DeleteLocalRef(contextPackageValue);
    if (!packageCallOk || contextPackage != expectedPackage) {
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    jobject packageManager = env->CallObjectMethod(context, getPackageManager);
    jobject appInfo = env->CallObjectMethod(context, getApplicationInfo);
    if (!clearException(env) || packageManager == nullptr || appInfo == nullptr) {
        if (packageManager != nullptr) env->DeleteLocalRef(packageManager);
        if (appInfo != nullptr) env->DeleteLocalRef(appInfo);
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    std::string appInfoPackage;
    if (!readStringField(env, appInfo, "packageName", &appInfoPackage) || appInfoPackage != expectedPackage) {
        env->DeleteLocalRef(packageManager);
        env->DeleteLocalRef(appInfo);
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    std::string sourceDir;
    std::string publicSourceDir;
    if (!readStringField(env, appInfo, "sourceDir", &sourceDir) || sourceDir.empty()) {
        env->DeleteLocalRef(packageManager);
        env->DeleteLocalRef(appInfo);
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }
    readStringField(env, appInfo, "publicSourceDir", &publicSourceDir);

    std::vector<std::string> primaryArchives;
    std::vector<std::string> optionalArchives;
    addPath(&primaryArchives, sourceDir);
    addPath(&optionalArchives, publicSourceDir);
    readStringArrayField(env, appInfo, "splitSourceDirs", &optionalArchives);
    readStringArrayField(env, appInfo, "splitPublicSourceDirs", &optionalArchives);

    auto codePathValue = static_cast<jstring>(env->CallObjectMethod(context, getPackageCodePath));
    if (clearException(env) && codePathValue != nullptr) {
        addPath(&primaryArchives, toString(env, codePathValue));
        env->DeleteLocalRef(codePathValue);
    }
    auto resourcePathValue = static_cast<jstring>(env->CallObjectMethod(context, getPackageResourcePath));
    if (clearException(env) && resourcePathValue != nullptr) {
        addPath(&optionalArchives, toString(env, resourcePathValue));
        env->DeleteLocalRef(resourcePathValue);
    }
    removePaths(&optionalArchives, primaryArchives);

    jclass pmClass = env->GetObjectClass(packageManager);
    jmethodID getInstalledInfo = pmClass == nullptr ? nullptr : env->GetMethodID(
        pmClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    jmethodID getArchiveInfo = pmClass == nullptr ? nullptr : env->GetMethodID(
        pmClass, "getPackageArchiveInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    if (pmClass == nullptr || getInstalledInfo == nullptr || getArchiveInfo == nullptr || !clearException(env)) {
        if (pmClass != nullptr) env->DeleteLocalRef(pmClass);
        env->DeleteLocalRef(packageManager);
        env->DeleteLocalRef(appInfo);
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    jstring packageArg = env->NewStringUTF(expectedPackage.c_str());
    jobject installedInfo = env->CallObjectMethod(packageManager, getInstalledInfo, packageArg, flags);
    env->DeleteLocalRef(packageArg);
    if (!clearException(env) || installedInfo == nullptr) {
        if (installedInfo != nullptr) env->DeleteLocalRef(installedInfo);
        env->DeleteLocalRef(pmClass);
        env->DeleteLocalRef(packageManager);
        env->DeleteLocalRef(appInfo);
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    std::string installedName;
    std::string installedSigning;
    const bool installedValid = packageInfoName(env, installedInfo, &installedName)
        && installedName == expectedPackage
        && packageInfoSigner(env, installedInfo, apiLevel, &installedSigning)
        && upperAscii(installedSigning) == expectedSigning;
    env->DeleteLocalRef(installedInfo);
    if (!installedValid || primaryArchives.empty()) {
        env->DeleteLocalRef(pmClass);
        env->DeleteLocalRef(packageManager);
        env->DeleteLocalRef(appInfo);
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    size_t verifiedPrimary = 0;
    for (const std::string &path : primaryArchives) {
        bool verified = false;
        if (!verifyArchive(
                env, packageManager, getArchiveInfo, path, flags, apiLevel,
                expectedPackage, expectedSigning, true, &verified)) {
            env->DeleteLocalRef(pmClass);
            env->DeleteLocalRef(packageManager);
            env->DeleteLocalRef(appInfo);
            env->DeleteLocalRef(contextClass);
            return JNI_FALSE;
        }
        if (verified) ++verifiedPrimary;
    }

    if (verifiedPrimary == 0) {
        env->DeleteLocalRef(pmClass);
        env->DeleteLocalRef(packageManager);
        env->DeleteLocalRef(appInfo);
        env->DeleteLocalRef(contextClass);
        return JNI_FALSE;
    }

    for (const std::string &path : optionalArchives) {
        bool ignoredVerified = false;
        if (!verifyArchive(
                env, packageManager, getArchiveInfo, path, flags, apiLevel,
                expectedPackage, expectedSigning, false, &ignoredVerified)) {
            env->DeleteLocalRef(pmClass);
            env->DeleteLocalRef(packageManager);
            env->DeleteLocalRef(appInfo);
            env->DeleteLocalRef(contextClass);
            return JNI_FALSE;
        }
    }

    env->DeleteLocalRef(pmClass);
    env->DeleteLocalRef(packageManager);
    env->DeleteLocalRef(appInfo);
    env->DeleteLocalRef(contextClass);
    return JNI_TRUE;
}

bool verifySignedIdentityBinding(JNIEnv *env,
                                 jclass nativeClass,
                                 jstring canonicalValue,
                                 jstring signatureBase64,
                                 const std::string &currentPackage,
                                 const std::string &currentSigningValue,
                                 jlong leaseExpiresAt,
                                 jlong serverTime) {
    if (!verifyServerSignature(env, nativeClass, canonicalValue, signatureBase64)) return false;

    const std::string canonical = toString(env, canonicalValue);
    const std::vector<std::string> fields = splitLines(canonical);
    if (fields.size() != 14) return false;

    const std::string currentSigning = upperAscii(currentSigningValue);
    if (fields[0] != "sdk-panel-v3-identity"
        || !isKeyId(fields[1])
        || upperAscii(fields[2]) != currentSigning
        || fields[3] != currentPackage
        || upperAscii(fields[4]) != currentSigning
        || !isHex(fields[5], 64)
        || !isHex(fields[6], 32)
        || !isToken(fields[7], 22, 96)) {
        return false;
    }

    jlong signedLease = 0;
    jlong signedServerTime = 0;
    jlong sdkVersion = 0;
    if (!parsePositiveLong(fields[8], &signedLease)
        || !parsePositiveLong(fields[9], &signedServerTime)
        || !parsePositiveLong(fields[13], &sdkVersion)
        || signedLease != leaseExpiresAt
        || signedServerTime != serverTime) {
        return false;
    }

    if (!(fields[10] == "SPECIFIC" || fields[10] == "ANY")) return false;
    if (!(fields[11] == "SPECIFIC" || fields[11] == "AUTO" || fields[11] == "ANY")) return false;
    if (!(fields[12] == "DISABLED" || fields[12] == "SINGLE"
        || fields[12] == "LIMITED" || fields[12] == "UNLIMITED")) return false;

    return sdkVersion > 0;
}
