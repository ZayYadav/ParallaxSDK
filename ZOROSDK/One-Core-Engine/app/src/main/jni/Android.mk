LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libcurl
LOCAL_SRC_FILES := backends/external/curl-android-$(TARGET_ARCH_ABI)/lib/libcurl.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libssl
LOCAL_SRC_FILES := backends/external/openssl-android-$(TARGET_ARCH_ABI)/lib/libssl.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libcrypto
LOCAL_SRC_FILES := backends/external/openssl-android-$(TARGET_ARCH_ABI)/lib/libcrypto.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := KESHAVXOWNERLoader

LOCAL_SRC_FILES := main.cpp \
    NativeApkAttestation.cpp \
    NativeLicenseAttestationBridge.cpp \
    ProcessBindingGuard.cpp \
    NativeTermination.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/backends/external/curl-android-$(TARGET_ARCH_ABI)/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/backends/external/openssl-android-$(TARGET_ARCH_ABI)/include

# Compile the expected signing identity directly into native code. The verifier consumes the
# value through OBFUSCATE(), so the certificate hash is not left as a plain DEX/native string.
# Gradle supplies ONECORE_SIGNING_SHA256 separately for each build variant.
ifneq ($(strip $(ONECORE_SIGNING_SHA256)),)
LOCAL_CPPFLAGS += -DONECORE_EXPECTED_SIGNING_SHA256=\"$(ONECORE_SIGNING_SHA256)\"
else
LOCAL_CPPFLAGS += -DONECORE_EXPECTED_SIGNING_SHA256=\"\"
endif

# Code optimization
# -std=c++17 is required to support AIDE app with NDK support
LOCAL_CFLAGS += -Wno-error=format-security -fvisibility=hidden -ffunction-sections -fdata-sections -w -std=c++17
LOCAL_CPPFLAGS += -Wno-error=format-security -fvisibility=hidden -ffunction-sections -fdata-sections -w -Werror -s -fms-extensions
LOCAL_LDFLAGS += -Wl,--gc-sections,--strip-all
LOCAL_ARM_MODE := arm

LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog -landroid -lz -ldl

LOCAL_STATIC_LIBRARIES := libcurl libssl libcrypto

include $(BUILD_SHARED_LIBRARY)
