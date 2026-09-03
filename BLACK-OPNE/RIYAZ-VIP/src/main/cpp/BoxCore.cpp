#include "BoxCore.h"
#include "Log.h"
#include "IO.h"
#include <jni.h>
#include "JniHook/JniHook.h"
#include "Hook/VMClassLoaderHook.h"
#include "Hook/UnixFileSystemHook.h"
#include "Hook/SystemPropertiesHook.h"
#include <Hook/BinderHook.h>
#include <Hook/DexFileHook.h>
#include <Hook/RuntimeHook.h>
#include <Hook/LinuxHook.h>


struct {
    JavaVM *vm;
    jclass NativeCoreClass;
    jmethodID getCallingUidId;
    jmethodID redirectPathString;
    jmethodID redirectPathFile;
    int api_level;
    bool initialized;  // flag to check if class and methods are ready
} VMEnv = {nullptr, nullptr, nullptr, nullptr, nullptr, 0, false};


JNIEnv *getEnv() {
    if (VMEnv.vm == nullptr) return nullptr;
    JNIEnv *env;
    jint ret = VMEnv.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED) {
        // Thread not attached – we can attach, but caller must handle env
        return nullptr;
    } else if (ret != JNI_OK) {
        return nullptr;
    }
    return env;
}

JNIEnv *ensureEnvCreated() {
    JNIEnv *env = getEnv();
    if (env == nullptr) {
        if (VMEnv.vm == nullptr) return nullptr;
        // Try to attach the current thread
        jint ret = VMEnv.vm->AttachCurrentThread(&env, nullptr);
        if (ret != JNI_OK || env == nullptr) {
            return nullptr;  // attach failed
        }
    }
    return env;
}

int BoxCore::getCallingUid(JNIEnv *env, int orig) {
    if (!VMEnv.initialized) return orig;  // fallback to original if not ready

    JNIEnv *e = ensureEnvCreated();
    if (e == nullptr || VMEnv.NativeCoreClass == nullptr || VMEnv.getCallingUidId == nullptr) {
        return orig;
    }
    return e->CallStaticIntMethod(VMEnv.NativeCoreClass, VMEnv.getCallingUidId, orig);
}

jstring BoxCore::redirectPathString(JNIEnv *env, jstring path) {
    if (!VMEnv.initialized || path == nullptr) return path;

    JNIEnv *e = ensureEnvCreated();
    if (e == nullptr || VMEnv.NativeCoreClass == nullptr || VMEnv.redirectPathString == nullptr) {
        return path;
    }

    // We must pass 'path' which might be a local reference from the caller's env.
    // But 'e' could be a different JNIEnv (attached thread). Use the passed env.
    // Better to use the provided env directly, but ensure it's valid.
    if (env == nullptr) return path;

    jstring result = (jstring) env->CallStaticObjectMethod(
        VMEnv.NativeCoreClass, VMEnv.redirectPathString, path);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return path;  // fallback
    }
    return result;
}

jobject BoxCore::redirectPathFile(JNIEnv *env, jobject path) {
    if (!VMEnv.initialized || path == nullptr) return path;

    JNIEnv *e = ensureEnvCreated();
    if (e == nullptr || VMEnv.NativeCoreClass == nullptr || VMEnv.redirectPathFile == nullptr) {
        return path;
    }

    if (env == nullptr) return path;

    jobject result = env->CallStaticObjectMethod(
        VMEnv.NativeCoreClass, VMEnv.redirectPathFile, path);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return path;  // fallback
    }
    return result;
}

int BoxCore::getApiLevel() {
    return VMEnv.api_level;
}

JavaVM *BoxCore::getJavaVM() {
    return VMEnv.vm;
}

void nativeHook(JNIEnv *env) {
    if (env == nullptr) return;

    // Initialize all hooks with proper error handling
    BaseHook::init(env);
    UnixFileSystemHook::init(env);
    VMClassLoaderHook::init(env);
    SystemPropertiesHook::init(env);
    RuntimeHook::init(env);
    LinuxHook::init(env);
    BinderHook::init(env);
    // DexFileHook might be needed – uncomment if available
    // DexFileHook::init(env);
}

void hideXposed(JNIEnv *env, jclass clazz) {
    ALOGD("set hideXposed");
    VMClassLoaderHook::hideXposed();
}

void init(JNIEnv *env, jobject clazz, jint api_level) {
    if (env == nullptr) return;

    ALOGD("NativeCore init.");
    VMEnv.api_level = api_level;
    VMEnv.initialized = false;  // reset until fully ready

    // Find and store global reference to NativeCore class
    jclass localClass = env->FindClass(VMCORE_CLASS);
    if (localClass == nullptr) {
        ALOGE("Failed to find class %s", VMCORE_CLASS);
        return;
    }
    VMEnv.NativeCoreClass = (jclass) env->NewGlobalRef(localClass);
    env->DeleteLocalRef(localClass);

    if (VMEnv.NativeCoreClass == nullptr) {
        ALOGE("Failed to create global ref for NativeCore class");
        return;
    }

    // Get method IDs
    VMEnv.getCallingUidId = env->GetStaticMethodID(
        VMEnv.NativeCoreClass, "getCallingUid", "(I)I");
    VMEnv.redirectPathString = env->GetStaticMethodID(
        VMEnv.NativeCoreClass, "redirectPath", "(Ljava/lang/String;)Ljava/lang/String;");
    VMEnv.redirectPathFile = env->GetStaticMethodID(
        VMEnv.NativeCoreClass, "redirectPath", "(Ljava/io/File;)Ljava/io/File;");

    // Check if all required methods are present
    if (VMEnv.getCallingUidId == nullptr ||
        VMEnv.redirectPathString == nullptr ||
        VMEnv.redirectPathFile == nullptr) {
        ALOGE("Failed to get one or more NativeCore method IDs");
        // Cleanup?
        env->DeleteGlobalRef(VMEnv.NativeCoreClass);
        VMEnv.NativeCoreClass = nullptr;
        return;
    }

    // Initialize JniHook subsystem
    JniHook::InitJniHook(env, api_level);

    VMEnv.initialized = true;  // all good
}

void addIORule(JNIEnv *env, jclass clazz, jstring target_path, jstring relocate_path) {
    if (env == nullptr || target_path == nullptr || relocate_path == nullptr) return;

    const char *target = env->GetStringUTFChars(target_path, nullptr);
    const char *relocate = env->GetStringUTFChars(relocate_path, nullptr);

    if (target != nullptr && relocate != nullptr) {
        ALOGD("set addIORule: %s -> %s", target, relocate);
        IO::addRule(target, relocate);
    }

    if (target != nullptr) env->ReleaseStringUTFChars(target_path, target);
    if (relocate != nullptr) env->ReleaseStringUTFChars(relocate_path, relocate);
}

void enableIO(JNIEnv *env, jclass clazz) {
    ALOGD("set enableIO");
    if (env == nullptr) return;

    IO::init(env);
    nativeHook(env);
}

static JNINativeMethod gMethods[] = {
        {"hideXposed", "()V",                                   (void *) hideXposed},
        {"addIORule",  "(Ljava/lang/String;Ljava/lang/String;)V", (void *) addIORule},
        {"enableIO",   "()V",                                   (void *) enableIO},
        {"init",       "(I)V",                                  (void *) init},
};

int registerNativeMethods(JNIEnv *env, const char *className,
                          JNINativeMethod *gMethods, int numMethods) {
    if (env == nullptr || className == nullptr || gMethods == nullptr) return JNI_FALSE;

    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        ALOGE("registerNativeMethods: class %s not found", className);
        return JNI_FALSE;
    }

    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("registerNativeMethods: failed to register natives for %s", className);
        env->DeleteLocalRef(clazz);
        return JNI_FALSE;
    }

    env->DeleteLocalRef(clazz);
    return JNI_TRUE;
}

int registerNatives(JNIEnv *env) {
    return registerNativeMethods(env, VMCORE_CLASS,
                                 gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}

void registerMethod(JNIEnv *jenv) {
    registerNatives(jenv);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    VMEnv.vm = vm;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        ALOGE("JNI_OnLoad: GetEnv failed");
        return JNI_EVERSION;
    }

    registerMethod(env);
    return JNI_VERSION_1_6;
}
