LOCAL_PATH := $(call my-dir)
ocpa_INCLUDES := 
subproject_PATHS := \
					router \
					ocpa

include $(addprefix $(LOCAL_PATH)/,$(addsuffix /Android.mk, \
		$(subproject_PATHS)))