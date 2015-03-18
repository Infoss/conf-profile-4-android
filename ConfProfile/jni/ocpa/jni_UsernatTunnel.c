/*
 * jni_UsernatTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun_usernat.h"
#include "util.h"

JNI_METHOD(UsernatTunnel, initUsernatTun, jlong) {
	TRACEPRINT("");
	tun_ctx_t* ctx = create_usernat_tun_ctx(NULL, 0, this);

	if(ctx != NULL) {
		ctx->setJavaVpnTunnel(ctx, this);
	}

	return (jlong) (intptr_t) ctx;
}

JNI_METHOD(UsernatTunnel, deinitUsernatTun, void, jlong jtunctx) {
	TRACEPRINT("(tun_ctx=%p)", jtunctx);
	tun_ctx_t* ctx = (tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx != NULL) {
		ctx->ref_put(ctx);
	}
}

JNI_METHOD(UsernatTunnel, setPidForLink, void, jlong jtunctx, jlong jnativelinkptr, jint joutpid) {
	TRACEPRINT("(tun_ctx=%p, native_link=%p, out_pid=%d)", jtunctx, jnativelinkptr, joutpid);
	usernat_set_pid_for_link((intptr_t) jtunctx, (intptr_t) jnativelinkptr, (int) joutpid);
}
