LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := ocpa
LOCAL_SRC_FILES := ocpa.c android_jni.c
LOCAL_C_INCLUDES := $(ocpa_INCLUDES)
LOCAL_LDLIBS := -llog 
LOCAL_STATIC_LIBRARIES := librouter

include $(BUILD_SHARED_LIBRARY)
