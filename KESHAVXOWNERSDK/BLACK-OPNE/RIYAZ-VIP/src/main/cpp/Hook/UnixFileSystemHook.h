//
// Created by Milk on 4/9/21.
//

#ifndef VBOX_UNIXFILESYSTEMHOOK_H
#define VBOX_UNIXFILESYSTEMHOOK_H


#include "BaseHook.h"

class UnixFileSystemHook : public BaseHook {
public:
    static void init(JNIEnv *env);
};


#endif //VBOX_UNIXFILESYSTEMHOOK_H
