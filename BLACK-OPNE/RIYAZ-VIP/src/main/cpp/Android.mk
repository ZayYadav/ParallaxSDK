LOCAL_PATH := $(call my-dir)
MAIN_LOCAL_PATH := $(LOCAL_PATH)

# ========== ParallaxCore shared library ==========
include $(CLEAR_VARS)

# --- Module Name ---
LOCAL_MODULE := ParallaxCore

# -------- C FLAGS (SAFE) --------
LOCAL_CFLAGS := \
	-O3 \
	-Wno-error=format-security \
	-fvisibility=hidden \
	-ffunction-sections \
	-fdata-sections \
	-fstack-protector-strong \
	-D_FORTIFY_SOURCE=2 \
	-fcommon

# -------- CPP FLAGS (NO CODE REMOVED) --------
LOCAL_CPPFLAGS := \
	-std=c++17 \
	-Wno-error=format-security \
	-Wno-error=c++11-narrowing \
	-fvisibility=hidden \
	-fvisibility-inlines-hidden \
	-ffunction-sections \
	-fdata-sections \
	-fstack-protector-strong \
	-D_FORTIFY_SOURCE=2 \
	-fexceptions \
	-fno-rtti \
	-fno-unwind-tables \
	-fno-asynchronous-unwind-tables \
	-fcommon

# -------- LINKER FLAGS --------
LOCAL_LDFLAGS := \
	-Wl,--gc-sections \
	-Wl,--strip-all \
	-Wl,-z,relro \
	-Wl,-z,now \
	-Wl,-z,noexecstack

LOCAL_LDLIBS := -llog -landroid -lz
LOCAL_ARM_MODE := arm

# --- Include Paths ---
LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/Hook
LOCAL_C_INCLUDES += $(LOCAL_PATH)/IO
LOCAL_C_INCLUDES += $(LOCAL_PATH)/JniHook
LOCAL_C_INCLUDES += $(LOCAL_PATH)/SandHook
LOCAL_C_INCLUDES += $(LOCAL_PATH)/SandHook/xdl
LOCAL_C_INCLUDES += $(LOCAL_PATH)/SandHook/libzip
LOCAL_C_INCLUDES += $(LOCAL_PATH)/KittyMemory   # <-- ADDED

# --- ADD: libzip headers ---
LOCAL_C_INCLUDES += $(LOCAL_PATH)/libzip

# --- Source Files ---
PARALLAX_CORE_SRC := $(wildcard $(LOCAL_PATH)/*.cpp)
PARALLAX_CORE_SRC += $(wildcard $(LOCAL_PATH)/Hook/*.cpp)
PARALLAX_CORE_SRC += $(wildcard $(LOCAL_PATH)/IO/*.cpp)
PARALLAX_CORE_SRC += $(wildcard $(LOCAL_PATH)/JniHook/*.cpp)
PARALLAX_CORE_SRC += $(wildcard $(LOCAL_PATH)/SandHook/*.cpp)
PARALLAX_CORE_SRC += $(wildcard $(LOCAL_PATH)/KittyMemory/*.cpp)   # <-- ADDED

LOCAL_C_INCLUDES += $(LOCAL_PATH)/Riyaz

# C Files (xdl - SandHook/xdl)
PARALLAX_CORE_SRC += $(wildcard $(LOCAL_PATH)/SandHook/xdl/*.c)
# C Files (libzip - SandHook/libzip)
PARALLAX_CORE_SRC += $(wildcard $(LOCAL_PATH)/SandHook/libzip/*.c)

LOCAL_SRC_FILES := $(PARALLAX_CORE_SRC:$(LOCAL_PATH)/%=%)

#LOCAL_STATIC_LIBRARIES := libdobby

# --- Build Shared Library ---
include $(BUILD_SHARED_LIBRARY)
