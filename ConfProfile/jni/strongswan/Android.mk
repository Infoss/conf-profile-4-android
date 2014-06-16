LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

include $(LOCAL_PATH)/Config.mk

include $(addprefix $(LOCAL_PATH)/src/,$(addsuffix /Android.mk, \
		$(strongswan_BUILD)))
