/*
 * jni_IpSecTunnel.c
 *
 * This file is part of Profile provisioning for Android
 * Copyright (C) 2012 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * Hochschule fuer Technik Rapperswil
 * Modifications (C) 2014 S. Martyanov
 * Modifications (C) 2014 Dmitry Vorobiev
 * Copyright (C) 2014  Infoss AS, https://infoss.no, info@infoss.no
 */

#include "strongswan.h"
#include "tun_ipsec.h"

JNI_METHOD(IpSecTunnel, initializeCharon, jboolean, jstring jlogfile, jboolean byod, jlong jtunctx) {
	return initialize_library(env, this, androidjni_convert_jstring(env, jlogfile), byod, jtunctx);
}

JNI_METHOD(IpSecTunnel, deinitializeCharon, void) {
	deinitialize_library(env);
}

JNI_METHOD(IpSecTunnel, initiate, void,
	jstring jtype, jstring jgateway, jstring jusername, jstring jpassword) {
	char *type, *gateway, *username, *password;

	type = androidjni_convert_jstring(env, jtype);
	gateway = androidjni_convert_jstring(env, jgateway);
	username = androidjni_convert_jstring(env, jusername);
	password = androidjni_convert_jstring(env, jpassword);

	initialize_tunnel(type, gateway, username, password);
}

JNI_METHOD(IpSecTunnel, networkChanged, void, jboolean jdisconnected) {
	notify_library(jdisconnected);
}

JNI_METHOD(IpSecTunnel, initIpSecTun, jlong) {
	tun_ctx_t* ctx = create_ipsec_tun_ctx(NULL, 0);
	if(ctx != NULL) {
		ctx->setJavaVpnTunnel(ctx, this);
	}
	return (jlong) (intptr_t) ctx;
}

JNI_METHOD(IpSecTunnel, deinitIpSecTun, void, jlong jtunctx) {
	tun_ctx_t* ctx = (ipsec_tun_ctx_t*) (intptr_t) jtunctx;
	if(ctx != NULL) {
		ctx->ref_put(ctx);
	}
}
