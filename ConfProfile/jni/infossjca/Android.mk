LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := infossjca
LOCAL_SRC_FILES := \
	android_jni.c \
	jca.c \

LOCAL_C_INCLUDES := jni/openssl-1.0.1g/include
LOCAL_LDLIBS := -llog 
LOCAL_STATIC_LIBRARIES := 

include $(BUILD_SHARED_LIBRARY)
