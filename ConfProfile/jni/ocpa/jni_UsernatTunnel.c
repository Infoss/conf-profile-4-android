/*
 * jni_UsernatTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "tun_usernat.h"

JNI_METHOD(UsernatTunnel, initUsernatTun, jlong) {
	return (jlong) (intptr_t) usernat_tun_init(this);
}

JNI_METHOD(UsernatTunnel, deinitUsernatTun, void, jlong jtunctx) {
	usernat_tun_deinit((usernat_tun_ctx_t*) (intptr_t) jtunctx);
}

JNI_METHOD(UsernatTunnel, setPidForLink, void, jlong jtunctx, jlong jnativelinkptr, jint joutpid) {
	usernat_set_pid_for_link((intptr_t) jtunctx, (intptr_t) jnativelinkptr, (int) joutpid);
}
