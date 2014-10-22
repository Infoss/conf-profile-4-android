/*
 * jni_OpenVpnTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun_openvpn.h"
#include "util.h"

JNI_METHOD(OpenVpnTunnel, initOpenVpnTun, jlong) {
	TRACEPRINT("()");
	openvpn_tun_ctx_t* result = create_openvpn_tun_ctx(NULL, 0);
	if(result != NULL) {
		result->setJavaVpnTunnel(result, this);
	}
	return (jlong) (intptr_t) result;
}

JNI_METHOD(OpenVpnTunnel, deinitOpenVpnTun, void, jlong jtunctx) {
	TRACEPRINT("(tun_ctx=%p)", jtunctx);
	openvpn_tun_ctx_t* ctx = (openvpn_tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx != NULL) {
		ctx->ref_put(ctx);
	}
}

JNI_METHOD(OpenVpnTunnel, getLocalFd, jint, jlong jtunctx) {
	TRACEPRINT("(tun_ctx=%p)", jtunctx);
	openvpn_tun_ctx_t* ctx = (openvpn_tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx == NULL) {
		return UNDEFINED_FD;
	}

	return ctx->getLocalFd(ctx);
}

JNI_METHOD(OpenVpnTunnel, getRemoteFd, jint, jlong jtunctx) {
	TRACEPRINT("(tun_ctx=%p)", jtunctx);
	openvpn_tun_ctx_t* ctx = (openvpn_tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx == NULL) {
		return UNDEFINED_FD;
	}

	return ctx->getRemoteFd(ctx);
}
