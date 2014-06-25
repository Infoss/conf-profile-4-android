/*
 * tun_ipsec.c
 *
 *      Author: S. Martyanov
 */

#include "strongswan.h"

static int tun_init(common_tun_ctx_t* ctx) {

	int res = -1;
	int fds[2];
	if((res = socketpair(AF_UNIX, SOCK_DGRAM, PF_UNSPEC, fds)) != 0) {
		return 0;
	}

	ctx->local_fd = fds[0];
	ctx->remote_fd = fds[1];
	ctx->send_func = common_tun_send;
	ctx->recv_func = common_tun_recv;

	return res;
}

static void tun_deinit(common_tun_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	if(ctx->local_fd != -1) {
		shutdown(ctx->local_fd, SHUT_RDWR);
	}

	if(ctx->remote_fd != -1) {
		shutdown(ctx->remote_fd, SHUT_RDWR);
	}

	ctx->send_func = NULL;
	ctx->recv_func = NULL;
}

ipsec_tun_ctx_t* ipsec_tun_init() {

	ipsec_tun_ctx_t* ctx = malloc(sizeof(ipsec_tun_ctx_t));
	if(ctx == NULL) {
		return NULL;
	}

	if(tun_init(&ctx->common) != 0)
		return NULL;

	return ctx;
}

void ipsec_tun_deinit(ipsec_tun_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	tun_deinit(&ctx->common);

	free(ctx);
}

JNI_METHOD(IpSecVpnTunnel, initializeCharon, jboolean, jstring jlogfile, jboolean byod, jlong jtunctx) {
	return initialize_library(env, this, androidjni_convert_jstring(env, jlogfile), byod, jtunctx);
}

JNI_METHOD(IpSecVpnTunnel, deinitializeCharon, void)
{
	deinitialize_library(env);
}

JNI_METHOD(IpSecVpnTunnel, initiate, void,
	jstring jtype, jstring jgateway, jstring jusername, jstring jpassword)
{
	char *type, *gateway, *username, *password;

	type = androidjni_convert_jstring(env, jtype);
	gateway = androidjni_convert_jstring(env, jgateway);
	username = androidjni_convert_jstring(env, jusername);
	password = androidjni_convert_jstring(env, jpassword);

	initialize_tunnel(type, gateway, username, password);
}

JNI_METHOD(IpSecVpnTunnel, networkChanged, void, jboolean jdisconnected)
{
	notify_library(jdisconnected);
}

JNI_METHOD(IpSecVpnTunnel, initIpSecTun, jlong) {
	return (jlong) (intptr_t) ipsec_tun_init();
}

JNI_METHOD(IpSecVpnTunnel, deinitIpSecTun, void, jlong jtunctx) {
	ipsec_tun_deinit((ipsec_tun_ctx_t*) (intptr_t) jtunctx);
}
