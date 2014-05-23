/*
 * ocpa.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef OCPA_H_
#define OCPA_H_

#include <unistd.h>
#include <errno.h>
#include "android_jni.h"
#include "router.h"

typedef struct vpn_service_ctx_t vpn_service_ctx_t;
typedef struct openvpn_tun_ctx_t openvpn_tun_ctx_t;

typedef void (*free_vpn_service_ctx_func_ptr)(vpn_service_ctx_t* ctx);
typedef bool (*bypass_socket_func_ptr)(vpn_service_ctx_t* ctx, int sock);

struct vpn_service_ctx_t {
	jobject routerloop_object;
	jmethodID routerloop_protect;
	free_vpn_service_ctx_func_ptr free_vpn_service_ctx;
    bypass_socket_func_ptr bypass_socket;
};

#endif /* OCPA_H_ */
