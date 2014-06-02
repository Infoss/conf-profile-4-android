LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := uip
LOCAL_SRC_FILES := uip.c uiplib.c uip_arp.c uip-fw.c uip-neighbor.c uip-split.c psock.c timer.c clock-arch.c

include $(BUILD_STATIC_LIBRARY)
