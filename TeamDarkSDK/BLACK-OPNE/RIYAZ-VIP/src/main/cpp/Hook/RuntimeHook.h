//
// Created by Milk on 5/5/21.
//

#ifndef VBOX_RUNTIMEHOOK_H
#define VBOX_RUNTIMEHOOK_H


#include "BaseHook.h"
#include <jni.h>

class RuntimeHook : public BaseHook {
public:
    static void init(JNIEnv *env);
};


#endif //VBOX_RUNTIMEHOOK_H
