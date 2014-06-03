LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := ocpa
LOCAL_SRC_FILES := ocpa.c android_jni.c router.c tun_openvpn.c
LOCAL_C_INCLUDES := $(ocpa_INCLUDES)
LOCAL_LDLIBS := -llog 
LOCAL_STATIC_LIBRARIES := libocpautils libuip

include $(BUILD_SHARED_LIBRARY)
