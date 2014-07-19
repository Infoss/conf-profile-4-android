/*
 * jni_L2tpTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun_l2tp.h"

JNI_METHOD(L2tpTunnel, initL2tpTun, jlong) {
	return (jlong) (intptr_t) l2tp_tun_init(this);
}

JNI_METHOD(L2tpTunnel, deinitL2tpTun, void, jlong jtunctx) {
	l2tp_tun_deinit((l2tp_tun_ctx_t*) (intptr_t) jtunctx);
}

JNI_METHOD(L2tpTunnel, getLocalFd, jint, jlong jtunctx) {
	if(((l2tp_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return -1;
	}

	return ((l2tp_tun_ctx_t*) (intptr_t) jtunctx)->common.local_fd;
}

JNI_METHOD(L2tpTunnel, getRemoteFd, jint, jlong jtunctx) {
	if(((l2tp_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return -1;
	}

	return ((l2tp_tun_ctx_t*) (intptr_t) jtunctx)->common.remote_fd;
}
