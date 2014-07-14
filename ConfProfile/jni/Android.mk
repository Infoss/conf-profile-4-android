LOCAL_PATH := $(call my-dir)
ocpa_INCLUDES := 
subproject_PATHS := openssl \
					mtpd \
					pppd \
					lzo \
					snappy \
					openvpn \
					blinkt \
					strongswan \
					ocpa \
					usernat \
					socat-1.7.2.4

include $(addprefix $(LOCAL_PATH)/,$(addsuffix /Android.mk, \
		$(subproject_PATHS)))