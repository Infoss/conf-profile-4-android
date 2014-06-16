LOCAL_PATH := $(call my-dir)
ocpa_INCLUDES := 
subproject_PATHS := openssl-1.0.1g \
					mtpd \
					pppd \
					uip \
					utils \
					lzo \
					snappy \
					openvpn \
					blinkt \
					ocpa

include $(addprefix $(LOCAL_PATH)/,$(addsuffix /Android.mk, \
		$(subproject_PATHS)))