LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
android_jni.c \
jni_tunnel.c \
strongswan.c \
backend/android_attr.c \
backend/android_creds.c \
backend/android_private_key.c \
kernel/android_ipsec.c \
backend/android_service.c \
kernel/android_net.c \
kernel/network_manager.c \

include jni/strongswan/Config.mk

ifneq ($(strongswan_USE_BYOD),)
LOCAL_SRC_FILES += \
byod/imc_android_state.c \
byod/imc_android.c
endif

#PARENT_PATH := $(call parent-dir, $(LOCAL_PATH))

# build libbridge -------------------------------------------------------
LOCAL_C_INCLUDES += \
	$(strongswan_PATH)/src/libipsec \
	$(strongswan_PATH)/src/libhydra \
	$(strongswan_PATH)/src/libcharon \
	$(strongswan_PATH)/src/libstrongswan \
	$(ocpa_INCLUDES) 
#	$(PARENT_PATH)

ifneq ($(strongswan_USE_BYOD),)
LOCAL_C_INCLUDES += \
	$(strongswan_PATH)/src/libimcv \
	$(strongswan_PATH)/src/libtncif \
	$(strongswan_PATH)/src/libtnccs \
	$(strongswan_PATH)/src/libpts \
	$(strongswan_PATH)/src/libtls
endif

#$(info $(LOCAL_C_INCLUDES))

LOCAL_CFLAGS := $(strongswan_CFLAGS) \
	-DPLUGINS=\""$(strongswan_CHARON_PLUGINS)"\"

ifneq ($(strongswan_USE_BYOD),false)
LOCAL_CFLAGS += -DPLUGINS_BYOD=\""$(strongswan_BYOD_PLUGINS)"\"
endif

LOCAL_MODULE := libstrongswanbridge

LOCAL_MODULE_TAGS := optional

LOCAL_ARM_MODE := arm

LOCAL_PRELINK_MODULE := false

LOCAL_LDLIBS := -llog

LOCAL_SHARED_LIBRARIES := strongswan hydra ipsec charon ocpa

ifneq ($(strongswan_USE_BYOD),)
LOCAL_SHARED_LIBRARIES += libimcv libtncif libtnccs libpts
endif

include $(BUILD_SHARED_LIBRARY)
