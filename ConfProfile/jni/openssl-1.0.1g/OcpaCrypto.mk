include $(LOCAL_PATH)/OcpaCrypto-_main.mk
include $(LOCAL_PATH)/OcpaCrypto-aes.mk
include $(LOCAL_PATH)/OcpaCrypto-asn1.mk
include $(LOCAL_PATH)/OcpaCrypto-evp.mk
include $(LOCAL_PATH)/OcpaCrypto-x509.mk

#######################################
# target static library

include $(CLEAR_VARS)
LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)

# The static library should be used in only unbundled apps
# and we don't have clang in unbundled build yet.
LOCAL_SDK_VERSION := 9

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE:= libcrypto_static
LOCAL_WHOLE_STATIC_LIBRARIES := libcrypto_part__main libcrypto_part_aes libcrypto_part_asn1 libcrypto_part_evp libcrypto_part_x509
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/android-config.mk $(LOCAL_PATH)/OcpaCrypto.mk

LOCAL_SRC_FILES := ocpa_dummy.c
include $(LOCAL_PATH)/android-config.mk

# Replace cflags with static-specific cflags so we dont build in libdl deps
LOCAL_CFLAGS_32 := $(openssl_cflags_static_32)
LOCAL_CFLAGS_64 := $(openssl_cflags_static_64)
include $(BUILD_STATIC_LIBRARY)

#######################################
# target shared library
include $(CLEAR_VARS)
LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)

# If we're building an unbundled build, don't try to use clang since it's not
# in the NDK yet. This can be removed when a clang version that is fast enough
# in the NDK.
ifeq (,$(TARGET_BUILD_APPS))
LOCAL_CLANG := true
ifeq ($(HOST_OS), darwin)
LOCAL_ASFLAGS += -no-integrated-as
LOCAL_CFLAGS += -no-integrated-as
endif
else
LOCAL_SDK_VERSION := 9
endif
LOCAL_LDFLAGS += -ldl

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE:= libcrypto
LOCAL_WHOLE_STATIC_LIBRARIES := libcrypto_part__main libcrypto_part_aes libcrypto_part_asn1 libcrypto_part_evp libcrypto_part_x509
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/android-config.mk $(LOCAL_PATH)/OcpaCrypto.mk

LOCAL_SRC_FILES := ocpa_dummy.c
include $(LOCAL_PATH)/android-config.mk
include $(BUILD_SHARED_LIBRARY)
