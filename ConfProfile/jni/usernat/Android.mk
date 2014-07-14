LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	usernat.c \

LOCAL_C_INCLUDES := \

LOCAL_LDLIBS := -lc -llog
LOCAL_SHARED_LIBRARIES := 
LOCAL_CFLAGS := -Wall -Wno-parentheses -I. -D_GNU_SOURCE

LOCAL_MODULE:= libxusernat

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
	
LOCAL_SRC_FILES:= \
	usernat_pseudo_c.c  

LOCAL_C_INCLUDES := \

LOCAL_LDLIBS := -lc
LOCAL_SHARED_LIBRARIES := libxusernat 

LOCAL_MODULE:= usernat

include $(BUILD_EXECUTABLE)
