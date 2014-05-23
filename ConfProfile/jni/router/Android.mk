LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := librouter
LOCAL_SRC_FILES := router.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../utils
ocpa_INCLUDES += $(LOCAL_PATH)

include $(BUILD_STATIC_LIBRARY)