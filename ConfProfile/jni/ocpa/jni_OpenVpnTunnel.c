/*
 * jni_OpenVpnTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun_openvpn.h"

JNI_METHOD(OpenVpnTunnel, initOpenVpnTun, jlong) {
	return (jlong) (intptr_t) openvpn_tun_init();
}

JNI_METHOD(OpenVpnTunnel, deinitOpenVpnTun, void, jlong jtunctx) {
	openvpn_tun_deinit((openvpn_tun_ctx_t*) (intptr_t) jtunctx);
}

JNI_METHOD(OpenVpnTunnel, getLocalFd, jint, jlong jtunctx) {
	if(((openvpn_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return -1;
	}

	return ((openvpn_tun_ctx_t*) (intptr_t) jtunctx)->common.local_fd;
}

JNI_METHOD(OpenVpnTunnel, getRemoteFd, jint, jlong jtunctx) {
	if(((openvpn_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return -1;
	}

	return ((openvpn_tun_ctx_t*) (intptr_t) jtunctx)->common.remote_fd;
}
