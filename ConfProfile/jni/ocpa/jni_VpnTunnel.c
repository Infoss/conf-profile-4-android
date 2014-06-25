/*
 * jni_VpnTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun.h"

JNI_METHOD(VpnTunnel, setMasqueradeIp4Mode, void, jlong jtunctx, jboolean jison) {
	if(((common_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return;
	}

	((common_tun_ctx_t*) (intptr_t) jtunctx)->use_masquerade4 = jison;
}

JNI_METHOD(VpnTunnel, setMasqueradeIp4, void, jlong jtunctx, jint jip) {
	if(((common_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return;
	}

	((common_tun_ctx_t*) (intptr_t) jtunctx)->masquerade4 = jip;
}

JNI_METHOD(VpnTunnel, debugRestartPcap, void, jlong jtunctx, jobject jos) {
	if(((common_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return;
	}

	common_tun_ctx_t* ctx = ((common_tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx->pcap_output != NULL) {
		pcap_output_reset(ctx->pcap_output, jos);
	} else {
		ctx->pcap_output = pcap_output_init(jos);
	}

}

JNI_METHOD(VpnTunnel, debugStopPcap, void, jlong jtunctx) {
	if(((common_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return;
	}

	common_tun_ctx_t* ctx = ((common_tun_ctx_t*) (intptr_t) jtunctx);
	if(ctx->pcap_output != NULL) {
		pcap_output_destroy(ctx->pcap_output);
		ctx->pcap_output = NULL;
	}
}


