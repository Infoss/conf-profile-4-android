LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := ocpa
LOCAL_SRC_FILES := ocpa.cpp

include $(BUILD_SHARED_LIBRARY)
