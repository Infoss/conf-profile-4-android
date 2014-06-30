LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

lwip_C_SOURCES := \
    src/api/api_lib.c \
    src/api/api_msg.c \
    src/api/err.c \
    src/api/netbuf.c \
    src/api/netifapi.c \
    src/api/sockets.c \
    src/api/tcpip.c \
    src/core/dhcp.c \
    src/core/init.c \
    src/core/mem.c \
    src/core/memp.c \
    src/core/netif.c \
    src/core/pbuf.c \
    src/core/raw.c \
    src/core/stats.c \
    src/core/udp.c \
    src/core/ipv4/autoip.c \
    src/core/ipv4/icmp.c \
    src/core/ipv4/igmp.c \
    src/core/ipv4/inet.c \
    src/core/ipv4/ip.c \
    src/core/ipv4/ip_addr.c \
    src/core/ipv4/ip_frag.c \
    src/core/snmp/asn1_dec.c \
    src/core/snmp/asn1_enc.c \
    src/core/snmp/mib2.c \
    src/core/snmp/mib_structs.c \
    src/core/snmp/msg_in.c \
    src/core/snmp/msg_out.c \
    src/netif/etharp.c \
    ports/ocpa/lwip_chksum.c \
    ports/ocpa/perf.c \
    ports/ocpa/sys_arch.c \
    ports/ocpa/netif/delif.c \
    ports/ocpa/netif/fifo.c \
    ports/ocpa/netif/list.c \
    ports/ocpa/netif/pcapif.c \
    ports/ocpa/netif/sio.c \
    ports/ocpa/netif/tapif.c \
    ports/ocpa/netif/tcpdump.c \
    ports/ocpa/netif/tunif.c \
    ports/ocpa/netif/unixif.c \

LOCAL_LDLIBS := -llog 
LOCAL_MODULE    := usernat
LOCAL_SRC_FILES := usernat.c $(lwip_C_SOURCES)
LOCAL_C_INCLUDES := \
	jni/lwip/src/include \
	jni/lwip/src/include/ipv4 \
	jni/lwip/ports/ocpa/include		

lwip_C_SOURCES :=

include $(BUILD_EXECUTABLE)
