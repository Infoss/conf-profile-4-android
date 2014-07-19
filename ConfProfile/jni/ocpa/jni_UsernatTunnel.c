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
