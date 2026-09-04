#include "RuntimeHook.h"
#import "JniHook/JniHook.h"
#include "BoxCore.h"

HOOK_JNI(jstring, nativeLoad, JNIEnv *env, jobject obj, jstring name, jobject class_loader) {

    if (!env || !name) {
        return orig_nativeLoad(env, obj, name, class_loader);
    }

    const char *nameC = env->GetStringUTFChars(name, nullptr);

    if (nameC) {
        ALOGD("nativeLoad: %s", nameC);
        env->ReleaseStringUTFChars(name, nameC);
    }

    return orig_nativeLoad(env, obj, name, class_loader);
}


HOOK_JNI(jstring, nativeLoad2, JNIEnv *env, jobject obj, jstring name, jobject class_loader, jobject caller) {

    if (!env || !name) {
        return orig_nativeLoad2(env, obj, name, class_loader, caller);
    }

    const char *nameC = env->GetStringUTFChars(name, nullptr);

    if (nameC) {
        ALOGD("nativeLoad: %s", nameC);
        env->ReleaseStringUTFChars(name, nameC);
    }

    return orig_nativeLoad2(env, obj, name, class_loader, caller);
}

void RuntimeHook::init(JNIEnv *env) {
    const char *className = "java/lang/Runtime";
    if (BoxCore::getApiLevel() >= __ANDROID_API_Q__) {
        JniHook::HookJniFun(env, className, "nativeLoad","(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/String;",(void *) new_nativeLoad2,(void **) (&orig_nativeLoad2), true);
    } else {
        JniHook::HookJniFun(env, className, "nativeLoad","(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/String;",(void *) new_nativeLoad,(void **) (&orig_nativeLoad), true);
    }
}
