LOCAL_PATH := $(call my-dir)

# Report configuration
ifeq ($(SHARED_V8_LIB_FILENAME),)
# static
$(info [Configuration] Linking statically with built-in v8)
else
# dynamic
$(info [Configuration] Linking dynamically with shared v8 library ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/$(SHARED_V8_LIB_FILENAME))
endif

# libadblockplus.a
include $(CLEAR_VARS)

LOCAL_MODULE := libadblockplus
LOCAL_SRC_FILES := ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/libadblockplus.a

include $(PREBUILT_STATIC_LIBRARY)

# libv8-platform.a
include $(CLEAR_VARS)

LOCAL_MODULE := v8-libplatform
LOCAL_SRC_FILES := ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/libv8_libplatform.a

include $(PREBUILT_STATIC_LIBRARY)

ifeq ($(SHARED_V8_LIB_FILENAME),)
# static

# libv8-libsampler.a
include $(CLEAR_VARS)

LOCAL_MODULE := v8-libsampler
LOCAL_SRC_FILES := ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/libv8_libsampler.a

include $(PREBUILT_STATIC_LIBRARY)

# libv8-base.a
include $(CLEAR_VARS)

LOCAL_MODULE := v8-base
LOCAL_SRC_FILES := ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/libv8_base.a

include $(PREBUILT_STATIC_LIBRARY)

# libv8_libbase.a
include $(CLEAR_VARS)

LOCAL_MODULE := v8-libbase
LOCAL_SRC_FILES := ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/libv8_libbase.a

include $(PREBUILT_STATIC_LIBRARY)

# libv8_snapshot.a
include $(CLEAR_VARS)

LOCAL_MODULE := v8-snapshot
LOCAL_SRC_FILES := ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/libv8_snapshot.a

include $(PREBUILT_STATIC_LIBRARY)

else
# dynamic

# prebuilt shared library v8
include $(CLEAR_VARS)

LOCAL_MODULE := libv8
LOCAL_SRC_FILES := ./libadblockplus-binaries/android_$(TARGET_ARCH_ABI)/$(SHARED_V8_LIB_FILENAME)
include $(PREBUILT_SHARED_LIBRARY)

endif

include $(CLEAR_VARS)

LOCAL_MODULE := libadblockplus-jni
LOCAL_SRC_FILES := JniLibrary.cpp
LOCAL_SRC_FILES += JniPlatform.cpp
LOCAL_SRC_FILES += JniJsEngine.cpp JniFilterEngine.cpp JniJsValue.cpp
LOCAL_SRC_FILES += JniFilter.cpp JniSubscription.cpp JniEventCallback.cpp
LOCAL_SRC_FILES += JniLogSystem.cpp JniWebRequest.cpp
LOCAL_SRC_FILES += JniUpdateAvailableCallback.cpp JniUpdateCheckDoneCallback.cpp
LOCAL_SRC_FILES += JniFilterChangeCallback.cpp JniCallbacks.cpp Utils.cpp
LOCAL_SRC_FILES += JniNotification.cpp JniShowNotificationCallback.cpp
LOCAL_SRC_FILES += JniIsAllowedConnectionTypeCallback.cpp

LOCAL_CPP_FEATURES := exceptions
LOCAL_CPPFLAGS += -std=c++11

LOCAL_C_INCLUDES := jni/libadblockplus-binaries/include/

LOCAL_STATIC_LIBRARIES := libadblockplus v8-libplatform

ifeq ($(SHARED_V8_LIB_FILENAME),)
# static
LOCAL_STATIC_LIBRARIES += v8-base v8-snapshot v8-libsampler v8-libbase
else
# dynamic
LOCAL_STATIC_LIBRARIES += libv8
endif

include $(BUILD_SHARED_LIBRARY)
