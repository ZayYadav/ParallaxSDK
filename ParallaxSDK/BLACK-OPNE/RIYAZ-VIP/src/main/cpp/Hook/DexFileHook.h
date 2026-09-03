#ifndef VBOX_DEXFILEHOOK_H
#define VBOX_DEXFILEHOOK_H

#include "BaseHook.h"

class DexFileHook : public BaseHook{
public:
    static void init(JNIEnv *env);
    static void setFileReadonly(const char* filePath);
};


#endif //VBOX_DEXFILEHOOK_H
