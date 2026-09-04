//
// Created by Milk on 4/9/21.
//

#ifndef VIRTUAL_APP_BOXCORE
#define VIRTUAL_APP_BOXCORE

#include <jni.h>
#include <unistd.h>
//#include <fb/include/fb/ALog.h>
//#include <fb/include/fb/fbjni.h>
#define VMCORE_CLASS "top/niunaijun/blackbox/core/RNative"

class BoxCore {
public:
    static JavaVM *getJavaVM();
    static int getApiLevel();
    static int getCallingUid(JNIEnv *env, int orig);
    static jstring redirectPathString(JNIEnv *env, jstring path);
    static jobject redirectPathFile(JNIEnv *env, jobject path);
    static jlongArray loadEmptyDex(JNIEnv *env);
};

#endif //VIRTUAL_APP_BOXCORE