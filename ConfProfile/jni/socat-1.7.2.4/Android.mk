LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

socat_xio_SRC_FILES := xioinitialize.c xiohelp.c xioparam.c xiodiag.c xioopen.c xioopts.c \
	xiosignal.c xiosigchld.c xioread.c xiowrite.c \
	xiolayer.c xioshutdown.c xioclose.c xioexit.c \
	xio-process.c xio-fd.c xio-fdnum.c xio-stdio.c xio-pipe.c \
	xio-gopen.c xio-creat.c xio-file.c xio-named.c \
	xio-socket.c xio-interface.c xio-listen.c xio-unix.c \
	xio-ip.c xio-ip4.c xio-ip6.c xio-ipapp.c xio-tcp.c \
	xio-sctp.c xio-rawip.c \
	xio-socks.c xio-proxy.c xio-udp.c \
	xio-progcall.c xio-exec.c xio-system.c xio-termios.c xio-readline.c \
	xio-pty.c xio-openssl.c xio-streams.c\
	xio-ascii.c xiolockfile.c xio-tcpwrap.c xio-ext2.c xio-tun.c
	
socat_util_SRC_FILES := error.c dalan.c procan.c procan-cdefs.c \
	hostan.c fdname.c sysutils.c utils.c \
	nestlex.c filan.c sycls.c sslcls.c


LOCAL_SRC_FILES:= \
	$(socat_xio_SRC_FILES) \
	$(socat_util_SRC_FILES) \
	xio-socket-fd.c \
	socat.c \
	
socat_xio_SRC_FILES :=
socat_util_SRC_FILES :=

LOCAL_C_INCLUDES := \

LOCAL_LDLIBS := -lc 
LOCAL_SHARED_LIBRARIES := 
LOCAL_CFLAGS := -Wall -Wno-parentheses -DHAVE_CONFIG_H -I. -D_GNU_SOURCE -DOCPA_CHANGES=1

LOCAL_MODULE:= libsocat

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
	
LOCAL_SRC_FILES:= \
	socat_pseudo_c.c  

LOCAL_C_INCLUDES := \

LOCAL_LDLIBS := -lc 
LOCAL_SHARED_LIBRARIES := libsocat 

LOCAL_MODULE:= ocpasocat

include $(BUILD_EXECUTABLE)
