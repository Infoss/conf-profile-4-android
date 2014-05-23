LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libocpautils
LOCAL_SRC_FILES := android_log_utils.c
ocpa_INCLUDES += $(LOCAL_PATH)

include $(BUILD_STATIC_LIBRARY)