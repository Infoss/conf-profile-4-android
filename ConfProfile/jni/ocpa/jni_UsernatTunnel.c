/*
 * jni_UsernatTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun_usernat.h"

JNI_METHOD(UsernatTunnel, initUsernatTun, jlong) {
	tun_ctx_t* ctx = create_usernat_tun_ctx(NULL, 0, this);
	return (jlong) (intptr_t) ctx;
}

JNI_METHOD(UsernatTunnel, deinitUsernatTun, void, jlong jtunctx) {
	tun_ctx_t* ctx = (tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx != NULL) {
		ctx->ref_put(ctx);
	}
}

JNI_METHOD(UsernatTunnel, setPidForLink, void, jlong jtunctx, jlong jnativelinkptr, jint joutpid) {
	usernat_set_pid_for_link((intptr_t) jtunctx, (intptr_t) jnativelinkptr, (int) joutpid);
}
