//
// Created by Milk on 4/9/21.
//

#ifndef VBOX_BASEHOOK_H
#define VBOX_BASEHOOK_H

#include <jni.h>
#include <Log.h>

class BaseHook {
public:
    static void init(JNIEnv *env);
};


#endif //VBOX_BASEHOOK_H
