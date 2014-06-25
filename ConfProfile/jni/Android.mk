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
					ocpa

include $(addprefix $(LOCAL_PATH)/,$(addsuffix /Android.mk, \
		$(subproject_PATHS)))