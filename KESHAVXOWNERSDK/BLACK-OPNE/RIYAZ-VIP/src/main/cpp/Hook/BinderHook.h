//
// Created by Milk on 4/25/21.
//

#ifndef VBOX_BINDERHOOK_H
#define VBOX_BINDERHOOK_H


#include "BaseHook.h"

class BinderHook : public BaseHook{
public:
    static void init(JNIEnv *env);
};

#endif //VBOX_BINDERHOOK_H
