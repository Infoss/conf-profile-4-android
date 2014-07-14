LOCAL_PATH := $(call my-dir)
ocpa_INCLUDES := 
subproject_PATHS := openssl \
					lwip \
					mtpd \
					pppd \
					lzo \
					snappy \
					openvpn \
					blinkt \
					strongswan \
					ocpa \
					socat-1.7.2.4

include $(addprefix $(LOCAL_PATH)/,$(addsuffix /Android.mk, \
		$(subproject_PATHS)))