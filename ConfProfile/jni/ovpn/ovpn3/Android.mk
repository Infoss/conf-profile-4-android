LOCAL_PATH:= $(call my-dir)/

include $(CLEAR_VARS)

LOCAL_LDLIBS := -lz 
LOCAL_C_INCLUDES := openssl/include lzo/include openssl/crypto openssl openvpn/src/compat openvpn3/client openvpn3 boost_1_55_0 polarssl/include  snappy
LOCAL_CPP_FEATURES += exceptions rtti

#LOCAL_SHARED_LIBRARIES :=  libssl libcrypto 
LOCAL_SHARED_LIBRARIES := polarssl-dynamic
#LOCAL_STATIC_LIBRARIES :=  libssl_static libcrypto_static  liblzo-static
LOCAL_STATIC_LIBRARIES :=  liblzo-static snappy-static

#LOCAL_CFLAGS= -DHAVE_CONFIG_H -DTARGET_ABI=\"${TARGET_ABI}\" -DUSE_OPENSSL -DOPENSSL_NO_ENGINE
LOCAL_CFLAGS= -DHAVE_CONFIG_H -DTARGET_ABI=\"${TARGET_ABI}\" -DUSE_POLARSSL -DHAVE_SNAPPY -DHAVE_LZO

#ifneq ($(TARGET_ARCH),mips)
#LOCAL_STATIC_LIBRARIES += breakpad_client
#LOCAL_CFLAGS += -DGOOGLE_BREAKPAD=1
#endif

LOCAL_MODULE = ovpn3

LOCAL_SRC_FILES:= \
	../openvpn3/javacli/ovpncli_wrap.cxx \
	boostsrc/error_code.cpp \
	../openvpn3/client/ovpncli.cpp \


#ifneq ($(TARGET_ARCH),mips)
#LOCAL_SRC_FILES+=src/openvpn/breakpad.cpp
#endif



include $(BUILD_SHARED_LIBRARY)
#include $(BUILD_EXECUTABLE)



