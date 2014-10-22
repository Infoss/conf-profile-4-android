/*
 * jni_VpnTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun.h"
#include "util.h"

JNI_METHOD(VpnTunnel, setMasqueradeIp4Mode, void, jlong jtunctx, jboolean jison) {
	TRACEPRINT("(tun_ctx=%p, mode=%s)", jtunctx, jison == JNI_TRUE ? "on" : "off");
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->setMasquerade4Mode(ctx, jison == JNI_TRUE);
}

JNI_METHOD(VpnTunnel, setMasqueradeIp4, void, jlong jtunctx, jint jip) {
	TRACEPRINT("(tun_ctx=%p, ip=0x%08x)", jtunctx, jip);
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->setMasquerade4Ip(ctx, (uint32_t) jip);
}

JNI_METHOD(VpnTunnel, debugRestartPcap, void, jlong jtunctx, jobject jos) {
	TRACEPRINT("(tun_ctx=%p, os=%p)", jtunctx, jos);
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->debugRestartPcap(ctx, jos);
}

JNI_METHOD(VpnTunnel, debugStopPcap, void, jlong jtunctx) {
	TRACEPRINT("(tun_ctx=%p)", jtunctx);
	tun_ctx_t* ctx = ((tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx == NULL) {
		return;
	}

	ctx->debugStopPcap(ctx);
}


