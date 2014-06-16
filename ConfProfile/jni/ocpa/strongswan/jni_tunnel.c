/*
 * tun_ipsec.c
 *
 *      Author: S. Martyanov
 */

#include "strongswan.h"

JNI_METHOD(IpSecVpnTunnel, initializeCharon, jboolean, jstring jlogfile, jboolean byod) {
	return initialize_library(env, this, androidjni_convert_jstring(env, jlogfile), byod);
}

JNI_METHOD(IpSecVpnTunnel, deinitializeCharon, void)
{
	return deinitialize_library(env);
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
	return notify_library(jdisconnected);
}
