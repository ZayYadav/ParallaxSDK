#include "SystemPropertiesHook.h"
#include "IO.h"
#include "BoxCore.h"
#import "JniHook/JniHook.h"
#include "Log.h"

// 🛡️ Add this include to get PROP_VALUE_MAX
#include <sys/system_properties.h>

static std::map<std::string, std::string> prop_map;

HOOK_JNI(jstring, native_get, JNIEnv *env, jobject obj, jstring key, jstring def) {

    // Handle null environment (should never happen)
    if (env == nullptr) {
        return nullptr;
    }

    // If key is null, return default immediately (do NOT call original)
    if (key == nullptr) {
        return def;  // def may be null, which is valid for SystemProperties.get
    }

    // Convert key to C string safely
    const char *key_str = env->GetStringUTFChars(key, nullptr);
    if (key_str == nullptr) {
        // Out of memory or other error → fallback to default
        return def;
    }

    // Convert default value if provided
    const char *def_str = nullptr;
    if (def != nullptr) {
        def_str = env->GetStringUTFChars(def, nullptr);
        // If def_str is null, we still continue but cannot release later
    }

    // Look up in property map
    auto ret = prop_map.find(key_str);

    if (ret != prop_map.end()) {
        const char *value = ret->second.c_str();

        // Release strings before returning
        env->ReleaseStringUTFChars(key, key_str);
        if (def_str != nullptr) {
            env->ReleaseStringUTFChars(def, def_str);
        }

        // Create new Java string from the mapped value
        return env->NewStringUTF(value);
    }

    // Not found: release and call original
    env->ReleaseStringUTFChars(key, key_str);
    if (def_str != nullptr) {
        env->ReleaseStringUTFChars(def, def_str);
    }

    return orig_native_get(env, obj, key, def);
}


HOOK_JNI(int, __system_property_get, const char *name, char *value) {
    if (!name || !value) {
        return orig___system_property_get(name, value);
    }

    auto ret = prop_map.find(name);

    if (ret != prop_map.end()) {
        const char *ret_value = ret->second.c_str();
        strncpy(value, ret_value, PROP_VALUE_MAX - 1);
        value[PROP_VALUE_MAX - 1] = '\0';
        return strlen(value);
    }

    return orig___system_property_get(name, value);
}


void SystemPropertiesHook::init(JNIEnv *env) {
    prop_map.insert({"ro.product.board", "umi"});
    prop_map.insert({"ro.product.brand", "Xiaomi"});
    prop_map.insert({"ro.product.device", "umi"});
    prop_map.insert({"ro.build.display.id","QKQ1.191117.002 test-keys"});
    prop_map.insert({"ro.build.host", "c5-miui-ota-bd074.bj"});
    prop_map.insert({"ro.build.id", "QKQ1.191117.002"});
    prop_map.insert({"ro.product.manufacturer", "Xiaomi"});
    prop_map.insert({"ro.product.model", "Mi 10"});
    prop_map.insert({"ro.product.name", "umi"});
    prop_map.insert({"ro.build.tags", "release-keys"});
    prop_map.insert({"ro.build.type", "user"});
    prop_map.insert({"ro.build.user", "builder"});

    JniHook::HookJniFun(
        env,
        "android/os/SystemProperties",
        "native_get",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        (void *) new_native_get,
        (void **) (&orig_native_get),
        true
    );

    // Optional native hook (commented out)
    // shadowhook_hook_sym_name("libc.so","__system_property_get",
    // (void*)new___system_property_get,(void**)&orig___system_property_get);
}