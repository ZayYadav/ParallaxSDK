#include "ClipboardKeyAuth.h"

#include <algorithm>
#include <cctype>

namespace ClipboardKeyAuth {
namespace {

bool ClearIfException(JNIEnv *env) {
    if (env == nullptr || !env->ExceptionCheck()) {
        return false;
    }
    env->ExceptionClear();
    return true;
}

std::string JStringToUtf8(JNIEnv *env, jstring value) {
    if (env == nullptr || value == nullptr) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr || ClearIfException(env)) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    ClearIfException(env);
    return result;
}

std::string Trim(std::string value) {
    auto notSpace = [](unsigned char ch) {
        return !std::isspace(ch);
    };
    value.erase(
            value.begin(),
            std::find_if(value.begin(), value.end(), notSpace));
    value.erase(
            std::find_if(value.rbegin(), value.rend(), notSpace).base(),
            value.end());
    return value;
}

}  // namespace

bool LooksLikeKey(const std::string &value) {
    if (value.size() < 4 || value.size() > 64) {
        return false;
    }
    for (unsigned char ch : value) {
        if (!(std::isalnum(ch) || ch == '_' || ch == '-')) {
            return false;
        }
    }
    return true;
}

std::string ReadCandidate(JNIEnv *env, jobject context) {
    if (env == nullptr || context == nullptr) {
        return {};
    }

    jclass contextClass = env->GetObjectClass(context);
    if (contextClass == nullptr || ClearIfException(env)) {
        return {};
    }

    jmethodID getSystemService = env->GetMethodID(
            contextClass,
            "getSystemService",
            "(Ljava/lang/String;)Ljava/lang/Object;");
    if (getSystemService == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(contextClass);
        return {};
    }

    jstring serviceName = env->NewStringUTF("clipboard");
    jobject clipboard = env->CallObjectMethod(context, getSystemService, serviceName);
    env->DeleteLocalRef(serviceName);
    env->DeleteLocalRef(contextClass);
    if (clipboard == nullptr || ClearIfException(env)) {
        return {};
    }

    jclass clipboardClass = env->GetObjectClass(clipboard);
    if (clipboardClass == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(clipboard);
        return {};
    }

    jmethodID hasPrimaryClip =
            env->GetMethodID(clipboardClass, "hasPrimaryClip", "()Z");
    jmethodID getPrimaryClip =
            env->GetMethodID(clipboardClass, "getPrimaryClip", "()Landroid/content/ClipData;");
    if (hasPrimaryClip == nullptr || getPrimaryClip == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(clipboardClass);
        env->DeleteLocalRef(clipboard);
        return {};
    }

    const jboolean hasClip = env->CallBooleanMethod(clipboard, hasPrimaryClip);
    if (ClearIfException(env) || hasClip != JNI_TRUE) {
        env->DeleteLocalRef(clipboardClass);
        env->DeleteLocalRef(clipboard);
        return {};
    }

    jobject clipData = env->CallObjectMethod(clipboard, getPrimaryClip);
    env->DeleteLocalRef(clipboardClass);
    env->DeleteLocalRef(clipboard);
    if (clipData == nullptr || ClearIfException(env)) {
        return {};
    }

    jclass clipDataClass = env->GetObjectClass(clipData);
    if (clipDataClass == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(clipData);
        return {};
    }

    jmethodID getItemCount = env->GetMethodID(clipDataClass, "getItemCount", "()I");
    jmethodID getItemAt = env->GetMethodID(
            clipDataClass,
            "getItemAt",
            "(I)Landroid/content/ClipData$Item;");
    if (getItemCount == nullptr || getItemAt == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(clipDataClass);
        env->DeleteLocalRef(clipData);
        return {};
    }

    const jint itemCount = env->CallIntMethod(clipData, getItemCount);
    if (ClearIfException(env) || itemCount <= 0) {
        env->DeleteLocalRef(clipDataClass);
        env->DeleteLocalRef(clipData);
        return {};
    }

    jobject item = env->CallObjectMethod(clipData, getItemAt, 0);
    env->DeleteLocalRef(clipDataClass);
    env->DeleteLocalRef(clipData);
    if (item == nullptr || ClearIfException(env)) {
        return {};
    }

    jclass itemClass = env->GetObjectClass(item);
    if (itemClass == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(item);
        return {};
    }

    jmethodID coerceToText = env->GetMethodID(
            itemClass,
            "coerceToText",
            "(Landroid/content/Context;)Ljava/lang/CharSequence;");
    if (coerceToText == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(itemClass);
        env->DeleteLocalRef(item);
        return {};
    }

    jobject charSequence = env->CallObjectMethod(item, coerceToText, context);
    env->DeleteLocalRef(itemClass);
    env->DeleteLocalRef(item);
    if (charSequence == nullptr || ClearIfException(env)) {
        return {};
    }

    jclass objectClass = env->FindClass("java/lang/Object");
    if (objectClass == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(charSequence);
        return {};
    }
    jmethodID toString = env->GetMethodID(objectClass, "toString", "()Ljava/lang/String;");
    env->DeleteLocalRef(objectClass);
    if (toString == nullptr || ClearIfException(env)) {
        env->DeleteLocalRef(charSequence);
        return {};
    }

    auto text = static_cast<jstring>(env->CallObjectMethod(charSequence, toString));
    env->DeleteLocalRef(charSequence);
    if (text == nullptr || ClearIfException(env)) {
        return {};
    }

    std::string candidate = Trim(JStringToUtf8(env, text));
    env->DeleteLocalRef(text);

    if (!LooksLikeKey(candidate)) {
        std::fill(candidate.begin(), candidate.end(), '\0');
        return {};
    }
    return candidate;
}

}  // namespace ClipboardKeyAuth

extern "C"
JNIEXPORT jstring JNICALL
Java_com_onecore_loader_activity_LoginActivity_nativeClipboardKey(
        JNIEnv *env,
        jobject thiz) {
    std::string key = ClipboardKeyAuth::ReadCandidate(env, thiz);
    jstring result = env->NewStringUTF(key.c_str());
    std::fill(key.begin(), key.end(), '\0');
    return result;
}
