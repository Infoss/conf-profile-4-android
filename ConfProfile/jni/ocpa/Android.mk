LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := ocpa
LOCAL_SRC_FILES := \
	ocpa.c \
	android_jni.c \
	router.c \
	iputils.c \
	tun.c \
	tun_ipsec.c \
	tun_l2tp.c \
	tun_openvpn.c \
	tun_usernat.c \
	java_MiscUtils.c \
	java_UsernatTunnel.c \
	java_VpnTunnel.c \
	jni_IpSecTunnel.c \
	jni_L2tpTunnel.c \
	jni_NetUtils.c \
	jni_OpenVpnTunnel.c \
	jni_RouterLoop.c \
	jni_UsernatTunnel.c \
	jni_VpnTunnel.c \
	pcap_output.c \
	android_log_utils.c \
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

LOCAL_C_INCLUDES := $(ocpa_INCLUDES)
LOCAL_C_INCLUDES += \
	$(strongswan_PATH)/src/libipsec \
	$(strongswan_PATH)/src/libhydra \
	$(strongswan_PATH)/src/libcharon \
	$(strongswan_PATH)/src/libstrongswan \

ifneq ($(strongswan_USE_BYOD),)
LOCAL_C_INCLUDES += \
	$(strongswan_PATH)/src/libimcv \
	$(strongswan_PATH)/src/libtncif \
	$(strongswan_PATH)/src/libtnccs \
	$(strongswan_PATH)/src/libpts \
	$(strongswan_PATH)/src/libtls
endif

LOCAL_CFLAGS := $(strongswan_CFLAGS) \
	-DPLUGINS=\""$(strongswan_CHARON_PLUGINS)"\"

ifneq ($(strongswan_USE_BYOD),)
	LOCAL_CFLAGS += -DPLUGINS_BYOD=\""$(strongswan_BYOD_PLUGINS)"\"
endif

LOCAL_LDLIBS := -llog 
LOCAL_STATIC_LIBRARIES := 
LOCAL_SHARED_LIBRARIES := strongswan hydra ipsec charon

ifneq ($(strongswan_USE_BYOD),)
	LOCAL_SHARED_LIBRARIES += libimcv libtncif libtnccs libpts
endif

include $(BUILD_SHARED_LIBRARY)
