/*
 * jni_L2tpTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun_l2tp.h"

JNI_METHOD(L2tpTunnel, initL2tpTun, jlong) {
	l2tp_tun_ctx_t* result = create_l2tp_tun_ctx(NULL, 0);
	if(result != NULL) {
		result->setJavaVpnTunnel(result, this);
	}
	return (jlong) (intptr_t) result;
}

JNI_METHOD(L2tpTunnel, deinitL2tpTun, void, jlong jtunctx) {
	l2tp_tun_ctx_t* ctx = (l2tp_tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx != NULL) {
		ctx->ref_put(ctx);
	}
}

JNI_METHOD(L2tpTunnel, getLocalFd, jint, jlong jtunctx) {
	l2tp_tun_ctx_t* ctx = (l2tp_tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx == NULL) {
		return UNDEFINED_FD;
	}

	return ctx->getLocalFd(ctx);
}

JNI_METHOD(L2tpTunnel, getRemoteFd, jint, jlong jtunctx) {
	l2tp_tun_ctx_t* ctx = (l2tp_tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx == NULL) {
		return UNDEFINED_FD;
	}

	return ctx->getRemoteFd(ctx);
}
