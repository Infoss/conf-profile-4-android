/*
 * jni_VpnTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun.h"

JNI_METHOD(VpnTunnel, setMasqueradeIp4Mode, void, jlong jtunctx, jboolean jison) {
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->setMasquerade4Mode(ctx, jison == JNI_TRUE);
}

JNI_METHOD(VpnTunnel, setMasqueradeIp4, void, jlong jtunctx, jint jip) {
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->setMasquerade4Ip(ctx, (uint32_t) jip);
}

JNI_METHOD(VpnTunnel, debugRestartPcap, void, jlong jtunctx, jobject jos) {
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->debugRestartPcap(ctx, jos);
}

JNI_METHOD(VpnTunnel, debugStopPcap, void, jlong jtunctx) {
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->debugStopPcap(ctx);
}


